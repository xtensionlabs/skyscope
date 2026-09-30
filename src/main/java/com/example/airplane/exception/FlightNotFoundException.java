package com.example.airplane.exception;

/** Thrown when a flight number or booking reference matches nothing. */
public class FlightNotFoundException extends FidsException {
    public FlightNotFoundException(String query) {
        super("No flight or booking found for \"" + query + "\"");
    }
}
