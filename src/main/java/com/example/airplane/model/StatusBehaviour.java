package com.example.airplane.model;

import java.util.Set;

/** Behaviour every flight status must provide (implemented polymorphically by each FlightStatus constant). */
public interface StatusBehaviour {
    /** Statuses a flight in this state may legally move to. */
    Set<FlightStatus> nextStatuses();

    /** Public-address style announcement text for a flight in this state. */
    String announcement(Flight flight);
}
