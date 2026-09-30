package com.example.airplane.service;

import com.example.airplane.exception.*;
import com.example.airplane.model.*;
import com.example.airplane.service.FlightEvent.Type;
import com.example.airplane.service.command.AdminCommand;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The business layer. Owns the flights and bookings, enforces the rules (gate conflicts, legal status changes,
 * check-in), keeps the audit log and undo history, persists changes, and notifies listeners of every change.
 */
public class FlightService {
    /** Two active flights may not share a gate if their departures are closer than this. */
    static final long GATE_CONFLICT_MINUTES = 40;

    private final FlightDataSource source;
    private final Map<String, Flight> flights = new LinkedHashMap<>();
    private final List<Booking> bookings = new ArrayList<>();
    private final List<FlightListener> listeners = new ArrayList<>();
    private final Deque<AdminCommand> history = new ArrayDeque<>();
    private final List<AuditEntry> audit = new ArrayList<>();
    private String lastSaveError;

    public FlightService(FlightDataSource source) throws IOException {
        this.source = source;
        FlightDataSource.Snapshot s = source.load();
        for (Flight f : s.flights()) flights.put(f.id(), f);
        bookings.addAll(s.bookings());
    }

    // ---- queries -------------------------------------------------------------------------------

    /** Snapshot of all flights in insertion order. */
    public List<Flight> flights() { return List.copyOf(flights.values()); }

    public Optional<Flight> find(String id) { return Optional.ofNullable(id == null ? null : flights.get(id)); }

    public Flight get(String id) throws FlightNotFoundException {
        return find(id).orElseThrow(() -> new FlightNotFoundException(String.valueOf(id)));
    }

    private static String normalise(String q) { return q == null ? "" : q.replaceAll("\\s+", "").toUpperCase(); }

    /** Finds a flight by flight number or booking reference (case- and space-insensitive). */
    public Flight lookup(String query) throws FlightNotFoundException {
        String q = normalise(query);
        Flight direct = flights.get(q);
        if (direct != null) return direct;
        for (Booking b : bookings) {
            if (b.bookingReference().equals(q) && flights.containsKey(b.linkedFlightNumber())) {
                return flights.get(b.linkedFlightNumber());
            }
        }
        throw new FlightNotFoundException(query == null ? "" : query.trim());
    }

    /** The booking the query refers to: matched by reference, otherwise the first booking on the found flight. */
    public Optional<Booking> bookingMatching(String query, Flight flight) {
        String q = normalise(query);
        return bookings.stream().filter(b -> b.bookingReference().equals(q)).findFirst()
                .or(() -> bookings.stream().filter(b -> b.linkedFlightNumber().equals(flight.id())).findFirst());
    }

    public List<Booking> bookingsOf(Passenger p) {
        return bookings.stream().filter(b -> b.passenger().equals(p)).toList();
    }

    // ---- observers -----------------------------------------------------------------------------

    public void addListener(FlightListener l) { listeners.add(l); }

    private void fire(Type type, Flight f, String oldValue) {
        persist();
        FlightEvent e = new FlightEvent(type, f, oldValue);
        for (FlightListener l : List.copyOf(listeners)) l.onFlightEvent(e);
    }

    // ---- validated mutations -------------------------------------------------------------------

    /** Changes a flight's gate; fails if the gate is invalid, occupied, or the flight can no longer move. */
    public String changeGate(Flight f, String gate) throws FidsException {
        Flight.requireValidGate(gate);
        ensureGateFree(gate, f);
        String old = f.changeGate(gate);
        fire(Type.GATE_CHANGED, f, old);
        return old;
    }

    public void moveStatus(Flight f, FlightStatus next) throws FidsException {
        FlightStatus old = f.status();
        f.moveTo(next);
        fire(Type.STATUS_CHANGED, f, old.label());
    }

    public void forceStatus(Flight f, FlightStatus next) {
        FlightStatus old = f.status();
        f.forceStatus(next);
        fire(Type.STATUS_CHANGED, f, old.label());
    }

    public void delay(Flight f, int minutes) throws FidsException {
        FlightStatus old = f.status();
        f.delayByMinutes(minutes);
        fire(Type.DELAYED, f, old.label());
    }

    public void cancel(Flight f, String reason) throws FidsException {
        FlightStatus old = f.status();
        f.cancel(reason);
        fire(Type.CANCELLED, f, old.label());
    }

    public void addFlight(Flight f) throws FidsException {
        if (flights.containsKey(f.id())) throw new InvalidFlightException("Flight " + f.displayNumber() + " already exists");
        ensureGateFree(f.gate(), f);
        flights.put(f.id(), f);
        fire(Type.ADDED, f, null);
    }

    public void removeFlight(Flight f) {
        flights.remove(f.id());
        fire(Type.REMOVED, f, null);
    }

    /** Puts a flight back to an earlier state (used by undo). */
    public void restore(Flight f, Flight.Snapshot snapshot) {
        f.restore(snapshot);
        fire(Type.UPDATED, f, null);
    }

    /** Checks a passenger in; only possible while the flight is still accepting passengers. */
    public Booking checkIn(String reference) throws FidsException {
        String ref = normalise(reference);
        Booking b = bookings.stream().filter(x -> x.bookingReference().equals(ref)).findFirst()
                .orElseThrow(() -> new FlightNotFoundException(reference));
        Flight f = get(b.linkedFlightNumber());
        if (!f.canCheckIn()) throw new InvalidFlightException("Check-in is closed for " + f.displayNumber()
                + " (" + f.status().label() + ")");
        b.checkIn();
        fire(Type.UPDATED, f, null);
        return b;
    }

    private void ensureGateFree(String gate, Flight candidate) throws GateOccupiedException {
        for (Flight other : flights.values()) {
            if (other != candidate && other.status().isActive() && other.gate().equals(gate)
                    && Math.abs(Duration.between(other.scheduledTime(), candidate.scheduledTime()).toMinutes())
                    < GATE_CONFLICT_MINUTES) {
                throw new GateOccupiedException(gate, other.displayNumber());
            }
        }
    }

    // ---- staff commands with undo ------------------------------------------------------------

    /** Runs a staff command, logs it and remembers it so it can be undone. */
    public void execute(AdminCommand cmd, String actor) throws FidsException {
        cmd.execute(this);
        history.push(cmd);
        record(actor, cmd.describe());
    }

    public boolean canUndo() { return !history.isEmpty(); }

    /** Undoes the most recent staff command; returns its description, or empty if there is nothing to undo. */
    public Optional<String> undoLast(String actor) throws FidsException {
        if (history.isEmpty()) return Optional.empty();
        AdminCommand cmd = history.pop();
        cmd.undo(this);
        record(actor, "UNDO: " + cmd.describe());
        return Optional.of(cmd.describe());
    }

    // ---- audit log and persistence --------------------------------------------------------------

    public void record(String actor, String description) {
        audit.add(new AuditEntry(LocalDateTime.now(), actor, description));
    }

    /** Audit entries, newest first. */
    public List<AuditEntry> auditLog() {
        List<AuditEntry> copy = new ArrayList<>(audit);
        Collections.reverse(copy);
        return copy;
    }

    public void persist() {
        try {
            source.save(new FlightDataSource.Snapshot(flights(), List.copyOf(bookings)));
            lastSaveError = null;
        } catch (IOException e) {
            lastSaveError = e.getMessage();
            System.err.println("Could not save data: " + e.getMessage());
        }
    }

    public Optional<String> lastSaveError() { return Optional.ofNullable(lastSaveError); }
}
