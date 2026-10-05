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
 * It is the Subject in the Observer pattern and the Receiver in the Command pattern.
 */
public class FlightService {
    /** Two active flights may not share a gate if their departures are closer than this. */
    static final long GATE_CONFLICT_MINUTES = 40;

    // Where data is loaded/saved (interface, so storage can be swapped: abstraction)
    private final FlightDataSource source;
    // Flights by id; LinkedHashMap keeps insertion order and gives fast lookup
    private final Map<String, Flight> flights = new LinkedHashMap<>();
    private final List<Booking> bookings = new ArrayList<>();
    // Observers waiting to hear about changes
    private final List<FlightListener> listeners = new ArrayList<>();
    // Stack of executed commands (newest on top) used for undo
    private final Deque<AdminCommand> history = new ArrayDeque<>();
    private final List<AuditEntry> audit = new ArrayList<>(); // log of who did what
    private String lastSaveError; // message of the last failed save, or null if OK

    // Loads the starting data from the data source and fills the flights and bookings collections.
    public FlightService(FlightDataSource source) throws IOException {
        this.source = source;
        FlightDataSource.Snapshot s = source.load();
        for (Flight f : s.flights()) flights.put(f.id(), f);
        bookings.addAll(s.bookings());
    }

    // ---- queries -------------------------------------------------------------------------------

    /** Snapshot of all flights in insertion order. */
    // List.copyOf returns an unmodifiable copy, so outside code can't change our internal map (encapsulation).
    public List<Flight> flights() { return List.copyOf(flights.values()); }

    // Optional means "might be missing"; a null id gives an empty Optional instead of crashing.
    public Optional<Flight> find(String id) { return Optional.ofNullable(id == null ? null : flights.get(id)); }

    // Like find, but throws our own checked exception if the flight does not exist.
    public Flight get(String id) throws FlightNotFoundException {
        return find(id).orElseThrow(() -> new FlightNotFoundException(String.valueOf(id)));
    }

    // Cleans user input: null becomes "", spaces are removed and letters made UPPER CASE.
    private static String normalise(String q) { return q == null ? "" : q.replaceAll("\\s+", "").toUpperCase(); }

    /** Finds a flight by flight number or booking reference (case- and space-insensitive). */
    public Flight lookup(String query) throws FlightNotFoundException {
        String q = normalise(query);
        // First try the query as a flight number
        Flight direct = flights.get(q);
        if (direct != null) return direct;
        // Otherwise treat it as a booking reference and return that booking's flight
        for (Booking b : bookings) {
            if (b.bookingReference().equals(q) && flights.containsKey(b.linkedFlightNumber())) {
                return flights.get(b.linkedFlightNumber());
            }
        }
        throw new FlightNotFoundException(query == null ? "" : query.trim()); // nothing matched
    }

    /** The booking the query refers to: matched by reference, otherwise the first booking on the found flight. */
    public Optional<Booking> bookingMatching(String query, Flight flight) {
        String q = normalise(query);
        // Stream with lambdas: filter bookings, take the first match, else fall back to the flight's first booking
        return bookings.stream().filter(b -> b.bookingReference().equals(q)).findFirst()
                .or(() -> bookings.stream().filter(b -> b.linkedFlightNumber().equals(flight.id())).findFirst());
    }

    // All bookings that belong to the given passenger.
    public List<Booking> bookingsOf(Passenger p) {
        return bookings.stream().filter(b -> b.passenger().equals(p)).toList();
    }

    // ---- observers -----------------------------------------------------------------------------

    // Register an observer (Observer pattern: subscribe).
    public void addListener(FlightListener l) { listeners.add(l); }

    // Saves the data, then tells every listener what happened (Observer pattern: notify).
    private void fire(Type type, Flight f, String oldValue) {
        persist();
        FlightEvent e = new FlightEvent(type, f, oldValue);
        // Loop over a copy so a listener may safely add/remove listeners while we iterate
        for (FlightListener l : List.copyOf(listeners)) l.onFlightEvent(e);
    }

    // ---- validated mutations -------------------------------------------------------------------

    /** Changes a flight's gate; fails if the gate is invalid, occupied, or the flight can no longer move. */
    public String changeGate(Flight f, String gate) throws FidsException {
        Flight.requireValidGate(gate);   // throws if the gate name is not valid
        ensureGateFree(gate, f);         // throws if another flight is using it
        String old = f.changeGate(gate); // Flight does the change and returns the previous gate
        fire(Type.GATE_CHANGED, f, old); // tell observers
        return old;
    }

    // Normal status change; the Flight object checks the move is legal. Old status is sent along with the event.
    public void moveStatus(Flight f, FlightStatus next) throws FidsException {
        FlightStatus old = f.status();
        f.moveTo(next);
        fire(Type.STATUS_CHANGED, f, old.label());
    }

    // Staff override: sets any status without the legality check.
    public void forceStatus(Flight f, FlightStatus next) {
        FlightStatus old = f.status();
        f.forceStatus(next);
        fire(Type.STATUS_CHANGED, f, old.label());
    }

    // Delays a flight by some minutes.
    public void delay(Flight f, int minutes) throws FidsException {
        FlightStatus old = f.status();
        f.delayByMinutes(minutes);
        fire(Type.DELAYED, f, old.label());
    }

    // Cancels a flight and stores the reason.
    public void cancel(Flight f, String reason) throws FidsException {
        FlightStatus old = f.status();
        f.cancel(reason);
        fire(Type.CANCELLED, f, old.label());
    }

    // Adds a new flight unless the id is already used or its gate clashes with another flight.
    public void addFlight(Flight f) throws FidsException {
        if (flights.containsKey(f.id())) throw new InvalidFlightException("Flight " + f.displayNumber() + " already exists");
        ensureGateFree(f.gate(), f);
        flights.put(f.id(), f);
        fire(Type.ADDED, f, null);
    }

    // Removes a flight (used when undoing an add).
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
        // Find the booking with this reference, or throw "not found"
        Booking b = bookings.stream().filter(x -> x.bookingReference().equals(ref)).findFirst()
                .orElseThrow(() -> new FlightNotFoundException(reference));
        Flight f = get(b.linkedFlightNumber());
        // Business rule: check-in must still be open for this flight
        if (!f.canCheckIn()) throw new InvalidFlightException("Check-in is closed for " + f.displayNumber()
                + " (" + f.status().label() + ")");
        b.checkIn();
        fire(Type.UPDATED, f, null);
        return b;
    }

    // Rule: no other active flight may use this gate within GATE_CONFLICT_MINUTES of this flight's time.
    private void ensureGateFree(String gate, Flight candidate) throws GateOccupiedException {
        for (Flight other : flights.values()) {
            // other != candidate compares object identity (skip the flight itself)
            if (other != candidate && other.status().isActive() && other.gate().equals(gate)
                    && Math.abs(Duration.between(other.scheduledTime(), candidate.scheduledTime()).toMinutes())
                    < GATE_CONFLICT_MINUTES) {
                throw new GateOccupiedException(gate, other.displayNumber());
            }
        }
    }

    // ---- staff commands with undo ------------------------------------------------------------

    /** Runs a staff command, logs it and remembers it so it can be undone. */
    // Takes the abstract AdminCommand type, so any command subclass works (polymorphism).
    public void execute(AdminCommand cmd, String actor) throws FidsException {
        cmd.execute(this);                // do the action (if it throws, nothing below runs)
        history.push(cmd);                // remember it on the undo stack
        record(actor, cmd.describe());    // write to the audit log
    }

    // True if there is something to undo.
    public boolean canUndo() { return !history.isEmpty(); }

    /** Undoes the most recent staff command; returns its description, or empty if there is nothing to undo. */
    public Optional<String> undoLast(String actor) throws FidsException {
        if (history.isEmpty()) return Optional.empty();
        AdminCommand cmd = history.pop(); // newest command first (stack = last in, first out)
        cmd.undo(this);
        record(actor, "UNDO: " + cmd.describe());
        return Optional.of(cmd.describe());
    }

    // ---- audit log and persistence --------------------------------------------------------------

    // Adds a log line with the current time, who did it and what happened.
    public void record(String actor, String description) {
        audit.add(new AuditEntry(LocalDateTime.now(), actor, description));
    }

    /** Audit entries, newest first. */
    public List<AuditEntry> auditLog() {
        List<AuditEntry> copy = new ArrayList<>(audit); // copy so the original stays in order
        Collections.reverse(copy);
        return copy;
    }

    // Saves everything through the data source; a failure is stored, not thrown, so the app keeps running.
    public void persist() {
        try {
            source.save(new FlightDataSource.Snapshot(flights(), List.copyOf(bookings)));
            lastSaveError = null;
        } catch (IOException e) {
            lastSaveError = e.getMessage();
            System.err.println("Could not save data: " + e.getMessage());
        }
    }

    // The last save error, if any (so the UI can show a warning).
    public Optional<String> lastSaveError() { return Optional.ofNullable(lastSaveError); }
}
