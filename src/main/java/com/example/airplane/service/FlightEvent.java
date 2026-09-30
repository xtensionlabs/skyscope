package com.example.airplane.service;

import com.example.airplane.model.Flight;

/** Something that happened to a flight; delivered to every registered {@link FlightListener}. */
public record FlightEvent(Type type, Flight flight, String oldValue) {
    public enum Type { STATUS_CHANGED, GATE_CHANGED, DELAYED, CANCELLED, ADDED, REMOVED, UPDATED }
}
