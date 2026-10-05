package com.example.airplane.exception;

/** Thrown when a flight operation breaks a business rule (bad data, illegal status change, ...). (Custom exception) */
public class InvalidFlightException extends FidsException {
    // The caller supplies the exact message, since many different rules can fail
    public InvalidFlightException(String message) {
        super(message);
    }
}
