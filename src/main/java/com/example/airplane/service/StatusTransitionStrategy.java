package com.example.airplane.service;

import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/**
 * Strategy deciding how a flight's status should progress next (empty = nothing to do yet).
 * Interface = abstraction: the simulation only knows this type, and we can swap implementations at run time.
 */
public interface StatusTransitionStrategy {
    // Display name of the strategy (shown to the user).
    String name();

    // Given a flight, the current time and a random generator, return the next status, or empty for "no change".
    Optional<FlightStatus> next(Flight flight, LocalDateTime now, Random rnd);

    // Should the simulator also make random gate changes? Off by default; only the demo strategy turns it on.
    default boolean randomGateChanges() { return false; }
}
