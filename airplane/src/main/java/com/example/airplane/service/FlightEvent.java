package com.example.airplane.service;

import com.example.airplane.model.Flight;

/**
 * Something that happened to a flight; delivered to every registered {@link FlightListener}.
 * It is the "message" in the Observer pattern. A record keeps it simple and immutable.
 */
public record FlightEvent(Type type, Flight flight, String oldValue) {
    // The kinds of event; an enum is a fixed list of allowed values.
    public enum Type { STATUS_CHANGED, GATE_CHANGED, DELAYED, CANCELLED, ADDED, REMOVED, UPDATED }
}
