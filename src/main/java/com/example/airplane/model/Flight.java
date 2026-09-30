package com.example.airplane.model;

import com.example.airplane.exception.InvalidFlightException;
import com.example.airplane.exception.InvalidGateException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * A departing flight. All state is private; changes go through methods that enforce business rules
 * and throw {@link com.example.airplane.exception.FidsException} subclasses when a rule is broken.
 */
public class Flight {
    private static final Pattern NUMBER = Pattern.compile("[A-Z0-9]{2}\\d{1,4}");
    private static final Pattern GATE = Pattern.compile("[ABC][1-8]");

    /** Memento of the mutable parts of a flight, used for undo and persistence. */
    public record Snapshot(String gate, FlightStatus status, LocalDateTime estimatedTime, String cancellationReason) { }

    private final String flightNumber;
    private final Airline airline;
    private final String origin;
    private final String destination;
    private final String destinationCode;
    private final String checkInCounter;
    private final String baggageBelt;
    private final Aircraft aircraft;
    private LocalDateTime scheduledTime;
    private LocalDateTime estimatedTime;
    private String gate;
    private FlightStatus status;
    private String cancellationReason = "";

    public Flight(String flightNumber, Airline airline, String origin, String destination, String destinationCode,
                  LocalDateTime scheduledTime, String gate, FlightStatus status, String checkInCounter,
                  String baggageBelt, Aircraft aircraft) throws InvalidFlightException, InvalidGateException {
        if (flightNumber == null || !NUMBER.matcher(flightNumber).matches()) {
            throw new InvalidFlightException("Invalid flight number \"" + flightNumber + "\" (expected e.g. KQ100)");
        }
        if (airline == null) throw new InvalidFlightException("Airline is required");
        if (destination == null || destination.isBlank()) throw new InvalidFlightException("Destination is required");
        if (destinationCode == null || !destinationCode.matches("[A-Za-z]{3}")) {
            throw new InvalidFlightException("Destination code must be 3 letters (e.g. LHR)");
        }
        if (scheduledTime == null) throw new InvalidFlightException("Scheduled time is required");
        if (status == null || aircraft == null) throw new InvalidFlightException("Status and aircraft are required");
        this.flightNumber = flightNumber;
        this.airline = airline;
        this.origin = origin;
        this.destination = destination.trim();
        this.destinationCode = destinationCode.toUpperCase();
        this.scheduledTime = scheduledTime;
        this.estimatedTime = scheduledTime;
        this.gate = requireValidGate(gate);
        this.status = status;
        this.checkInCounter = checkInCounter;
        this.baggageBelt = baggageBelt;
        this.aircraft = aircraft;
    }

    // ---- validation helpers -----------------------------------------------------------------

    public static boolean isValidGate(String gate) { return gate != null && GATE.matcher(gate).matches(); }

    public static String requireValidGate(String gate) throws InvalidGateException {
        if (!isValidGate(gate)) throw new InvalidGateException(gate);
        return gate;
    }

    // ---- read access ---------------------------------------------------------------------------

    /** Unique id, e.g. "KQ100". */
    public String id() { return flightNumber; }
    public String flightNumber() { return flightNumber; }
    /** "KQ 100" for display. */
    public String displayNumber() { return flightNumber.substring(0, 2) + " " + flightNumber.substring(2); }
    public Airline airline() { return airline; }
    public String origin() { return origin; }
    public String destination() { return destination; }
    public String destinationCode() { return destinationCode; }
    public LocalDateTime scheduledTime() { return scheduledTime; }
    public LocalDateTime estimatedTime() { return estimatedTime; }
    public String gate() { return gate; }
    public char pier() { return gate.charAt(0); }
    /** Terminal is derived from the gate's pier (A = 1, B = 2, C = 3), so it can never be inconsistent. */
    public String terminal() { return "Terminal " + (pier() - 'A' + 1); }
    public FlightStatus status() { return status; }
    public String checkInCounter() { return checkInCounter; }
    public String baggageBelt() { return baggageBelt; }
    public Aircraft aircraft() { return aircraft; }
    public String cancellationReason() { return cancellationReason; }

    /** "B2" -> "B02" so gates sort naturally. */
    public String gateSortKey() { return pier() + String.format("%02d", Integer.parseInt(gate.substring(1))); }

    public long minutesToDeparture(LocalDateTime now) { return Duration.between(now, estimatedTime).toMinutes(); }

    // ---- business rules ----------------------------------------------------------------------

    public boolean canChangeGate() { return status.isActive() && status != FlightStatus.GATE_CLOSED; }
    public boolean canCheckIn() { return canChangeGate(); }
    public boolean isBoardable() { return status == FlightStatus.BOARDING; }

    /** Moves to another gate; returns the previous gate. */
    public String changeGate(String newGate) throws InvalidGateException, InvalidFlightException {
        requireValidGate(newGate);
        if (!canChangeGate()) {
            throw new InvalidFlightException("Gate can no longer be changed for " + displayNumber()
                    + " (" + status.label() + ")");
        }
        if (newGate.equals(gate)) throw new InvalidFlightException(displayNumber() + " is already at gate " + gate);
        String old = gate;
        gate = newGate;
        return old;
    }

    /** Moves to the next status, but only along a legal transition. */
    public void moveTo(FlightStatus next) throws InvalidFlightException {
        if (!status.nextStatuses().contains(next)) {
            throw new InvalidFlightException("Cannot change " + displayNumber() + " from "
                    + status.label() + " to " + next.label());
        }
        status = next;
    }

    /** Administrative override: sets any status without checking transitions. */
    public void forceStatus(FlightStatus next) { status = next; }

    /** Pushes the estimated departure later and marks the flight Delayed. */
    public void delayByMinutes(int minutes) throws InvalidFlightException {
        if (minutes <= 0 || minutes > 720) throw new InvalidFlightException("Delay must be between 1 and 720 minutes");
        if (status != FlightStatus.ON_TIME && status != FlightStatus.DELAYED) {
            throw new InvalidFlightException(displayNumber() + " cannot be delayed while " + status.label());
        }
        estimatedTime = estimatedTime.plusMinutes(minutes);
        status = FlightStatus.DELAYED;
    }

    public void cancel(String reason) throws InvalidFlightException {
        if (!status.isActive()) {
            throw new InvalidFlightException(displayNumber() + " is already " + status.label().toLowerCase());
        }
        cancellationReason = (reason == null || reason.isBlank()) ? "Operational reasons." : reason.trim();
        status = FlightStatus.CANCELLED;
    }

    // ---- memento / persistence support -------------------------------------------------------

    public Snapshot snapshot() { return new Snapshot(gate, status, estimatedTime, cancellationReason); }

    public void restore(Snapshot s) {
        gate = s.gate();
        status = s.status();
        estimatedTime = s.estimatedTime();
        cancellationReason = s.cancellationReason() == null ? "" : s.cancellationReason();
    }

    /** Moves all times by a fixed amount (used when reloading saved data). */
    public void shiftTimes(Duration delta) {
        scheduledTime = scheduledTime.plus(delta);
        estimatedTime = estimatedTime.plus(delta);
    }
}
