package com.example.airplane.exception;

/** Thrown when a gate is already assigned to another active flight at about the same time. */
public class GateOccupiedException extends FidsException {
    public GateOccupiedException(String gate, String otherFlight) {
        super("Gate " + gate + " is already occupied by " + otherFlight);
    }
}
