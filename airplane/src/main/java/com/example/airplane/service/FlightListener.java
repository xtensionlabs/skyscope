package com.example.airplane.service;

/**
 * Observer of flight changes (the classic Observer pattern).
 * Any class (or lambda) that implements this interface can be told when a flight changes.
 */
@FunctionalInterface // exactly one abstract method, so a lambda can be used to implement it
public interface FlightListener {
    // Called by FlightService for every change; the event says what happened.
    void onFlightEvent(FlightEvent event);
}
