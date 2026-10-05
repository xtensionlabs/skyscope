package com.example.airplane.exception;

/** Thrown when a flight number or booking reference matches nothing. (Custom exception, inheritance) */
public class FlightNotFoundException extends FidsException {
    // The text the user searched for is put inside quotes in the message
    public FlightNotFoundException(String query) {
        super("No flight or booking found for \"" + query + "\"");
    }
}
