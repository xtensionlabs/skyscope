package com.example.airplane.exception;

/** Thrown when a gate is already assigned to another active flight at about the same time. (Custom exception) */
public class GateOccupiedException extends FidsException {
    // gate = the gate that was requested, otherFlight = the flight already using it
    public GateOccupiedException(String gate, String otherFlight) {
        super("Gate " + gate + " is already occupied by " + otherFlight);
    }
}
