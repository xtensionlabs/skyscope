package com.example.airplane.service;

/** Observer of flight changes (the classic Observer pattern). */
@FunctionalInterface
public interface FlightListener {
    void onFlightEvent(FlightEvent event);
}
