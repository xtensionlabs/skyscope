package com.example.airplane.model;

import com.example.airplane.exception.InvalidFlightException;
import com.example.airplane.exception.InvalidGateException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * A departing flight. All state is private; changes go through methods that enforce business rules
 * and throw {@link com.example.airplane.exception.FidsException} subclasses when a rule is broken.
 * OOP: encapsulation - fields are private and can only be changed through checked methods.
 */
public class Flight {
    // Regex patterns (static = shared by all flights): 2 letters/digits + 1-4 digits, e.g. KQ100
    private static final Pattern NUMBER = Pattern.compile("[A-Z0-9]{2}\\d{1,4}");
    // Gate = pier letter A, B or C followed by a number 1-8
    private static final Pattern GATE = Pattern.compile("[ABC][1-8]");

    /** Memento of the mutable parts of a flight, used for undo and persistence. */
    // Record nested inside Flight: stores a copy of the values that can change (Memento design pattern)
    public record Snapshot(String gate, FlightStatus status, LocalDateTime estimatedTime, String statusReason) { }

    // Fields that never change after creation (final)
    private final String flightNumber;
    private final Airline airline;           // composition: a Flight HAS-A Airline
    private final String origin;
    private final String destination;
    private final String destinationCode;    // 3-letter airport code e.g. LHR
    private final String checkInCounter;
    private final String baggageBelt;
    private final Aircraft aircraft;         // a Flight HAS-A Aircraft (could be any subclass)
    // Fields that can change while the flight is managed
    private LocalDateTime scheduledTime;
    private LocalDateTime estimatedTime;
    private String gate;
    private FlightStatus status;
    private String statusReason = "";          // why the flight is delayed or cancelled (empty otherwise)

    // Constructor: validates every input first, so an invalid Flight object can never exist
    public Flight(String flightNumber, Airline airline, String origin, String destination, String destinationCode,
                  LocalDateTime scheduledTime, String gate, FlightStatus status, String checkInCounter,
                  String baggageBelt, Aircraft aircraft) throws InvalidFlightException, InvalidGateException {
        // Each check below throws a custom exception when the rule is broken (exception handling)
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
        // All checks passed, so store the values
        this.flightNumber = flightNumber;
        this.airline = airline;
        this.origin = origin;
        this.destination = destination.trim();                  // remove extra spaces
        this.destinationCode = destinationCode.toUpperCase();   // store codes in capitals
        this.scheduledTime = scheduledTime;
        this.estimatedTime = scheduledTime;                     // at first the estimate equals the schedule
        this.gate = requireValidGate(gate);                     // throws InvalidGateException if bad
        this.status = status;
        this.checkInCounter = checkInCounter;
        this.baggageBelt = baggageBelt;
        this.aircraft = aircraft;
    }

    // ---- validation helpers -----------------------------------------------------------------

    // True if the text is a valid gate such as "B2"
    public static boolean isValidGate(String gate) { return gate != null && GATE.matcher(gate).matches(); }

    // Returns the gate if valid, otherwise throws InvalidGateException
    public static String requireValidGate(String gate) throws InvalidGateException {
        if (!isValidGate(gate)) throw new InvalidGateException(gate);
        return gate;
    }

    // ---- read access ---------------------------------------------------------------------------

    // Getters: let other classes read the private fields but not change them
    /** Unique id, e.g. "KQ100". */
    public String id() { return flightNumber; }
    public String flightNumber() { return flightNumber; }
    /** "KQ 100" for display. */
    // substring(0, 2) = first two characters, substring(2) = the rest
    public String displayNumber() { return flightNumber.substring(0, 2) + " " + flightNumber.substring(2); }
    public Airline airline() { return airline; }
    public String origin() { return origin; }
    public String destination() { return destination; }
    public String destinationCode() { return destinationCode; }
    public LocalDateTime scheduledTime() { return scheduledTime; }
    public LocalDateTime estimatedTime() { return estimatedTime; }
    public String gate() { return gate; }
    // First character of the gate is the pier letter
    public char pier() { return gate.charAt(0); }
    /** Terminal is derived from the gate's pier (A = 1, B = 2, C = 3), so it can never be inconsistent. */
    // Characters are numbers: 'B' - 'A' = 1, then + 1 gives terminal 2
    public String terminal() { return "Terminal " + (pier() - 'A' + 1); }
    public FlightStatus status() { return status; }
    public String checkInCounter() { return checkInCounter; }
    public String baggageBelt() { return baggageBelt; }
    public Aircraft aircraft() { return aircraft; }
    public String statusReason() { return statusReason; }

    /** "B2" -> "B02" so gates sort naturally. */
    // %02d pads the number to 2 digits, so B2 sorts before B10-style values
    public String gateSortKey() { return pier() + String.format("%02d", Integer.parseInt(gate.substring(1))); }

    // Minutes between the given time and the estimated departure (negative if already past)
    public long minutesToDeparture(LocalDateTime now) { return Duration.between(now, estimatedTime).toMinutes(); }

    // ---- business rules ----------------------------------------------------------------------

    // Gate can change only while the flight is active and the gate has not closed
    public boolean canChangeGate() { return status.isActive() && status != FlightStatus.GATE_CLOSED; }
    // Check-in follows the same rule as gate changes
    public boolean canCheckIn() { return canChangeGate(); }
    // Passengers can board only while status is BOARDING
    public boolean isBoardable() { return status == FlightStatus.BOARDING; }

    /** Moves to another gate; returns the previous gate. */
    public String changeGate(String newGate) throws InvalidGateException, InvalidFlightException {
        requireValidGate(newGate);   // reject malformed gates
        if (!canChangeGate()) {
            throw new InvalidFlightException("Gate can no longer be changed for " + displayNumber()
                    + " (" + status.label() + ")");
        }
        if (newGate.equals(gate)) throw new InvalidFlightException(displayNumber() + " is already at gate " + gate);
        String old = gate;   // remember old gate so we can return it
        gate = newGate;
        return old;
    }

    /** Moves to the next status, but only along a legal transition. */
    public void moveTo(FlightStatus next) throws InvalidFlightException {
        // Ask the current status (polymorphism) which statuses are allowed next
        if (!status.nextStatuses().contains(next)) {
            throw new InvalidFlightException("Cannot change " + displayNumber() + " from "
                    + status.label() + " to " + next.label());
        }
        status = next;
        statusReason = "";   // the old reason (e.g. for a delay) no longer applies
    }

    /** Administrative override: sets any status without checking transitions. */
    public void forceStatus(FlightStatus next) {
        status = next;
        statusReason = "";   // no reason is known for a forced status
    }

    /** Pushes the estimated departure later, records why, and marks the flight Delayed. */
    public void delayByMinutes(int minutes, String reason) throws InvalidFlightException {
        // Only delays of 1 to 720 minutes (12 hours) are accepted
        if (minutes <= 0 || minutes > 720) throw new InvalidFlightException("Delay must be between 1 and 720 minutes");
        // A delay must always be explained to passengers
        if (reason == null || reason.isBlank()) throw new InvalidFlightException("A reason for the delay is required");
        // On Time, Delayed (more) or Boarding flights can be delayed; once the gate has closed it is too late
        if (status != FlightStatus.ON_TIME && status != FlightStatus.DELAYED && status != FlightStatus.BOARDING) {
            throw new InvalidFlightException(displayNumber() + " cannot be delayed while " + status.label());
        }
        estimatedTime = estimatedTime.plusMinutes(minutes);   // scheduledTime stays unchanged
        statusReason = reason.trim();
        status = FlightStatus.DELAYED;
    }

    // Cancels the flight and stores the reason
    public void cancel(String reason) throws InvalidFlightException {
        // Departed or already cancelled flights cannot be cancelled
        if (!status.isActive()) {
            throw new InvalidFlightException(displayNumber() + " is already " + status.label().toLowerCase());
        }
        // If no reason was given use a default one
        statusReason = (reason == null || reason.isBlank()) ? "Operational reasons." : reason.trim();
        status = FlightStatus.CANCELLED;
    }

    // ---- memento / persistence support -------------------------------------------------------

    // Takes a copy of the changeable values (used for undo and saving)
    public Snapshot snapshot() { return new Snapshot(gate, status, estimatedTime, statusReason); }

    // Puts the flight back to the values stored in a snapshot (undo / loading)
    public void restore(Snapshot s) {
        gate = s.gate();
        status = s.status();
        estimatedTime = s.estimatedTime();
        // Guard against a null reason
        statusReason = s.statusReason() == null ? "" : s.statusReason();
    }

    /** Moves all times by a fixed amount (used when reloading saved data). */
    public void shiftTimes(Duration delta) {
        scheduledTime = scheduledTime.plus(delta);
        estimatedTime = estimatedTime.plus(delta);
    }
}
