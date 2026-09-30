package com.example.airplane.exception;

/** Thrown when a flight operation breaks a business rule (bad data, illegal status change, ...). */
public class InvalidFlightException extends FidsException {
    public InvalidFlightException(String message) {
        super(message);
    }
}
