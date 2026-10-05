package com.example.airplane.service;

import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/**
 * Demo mode: advances any flight one step regardless of the clock, so the board changes constantly.
 * One concrete Strategy (implements the interface).
 */
public class RandomProgressionStrategy implements StatusTransitionStrategy {
    @Override public String name() { return "Demo (fast, ignores the clock)"; }

    // Demo mode also shuffles gates now and then to keep the board busy
    @Override public boolean randomGateChanges() { return true; }

    @Override
    public Optional<FlightStatus> next(Flight f, LocalDateTime now, Random rnd) {
        // Switch on the current status and pick the next one in the normal life cycle.
        return switch (f.status()) {
            // 1 in 5 chance of a delay, otherwise start boarding
            case ON_TIME -> Optional.of(rnd.nextInt(5) == 0 ? FlightStatus.DELAYED : FlightStatus.BOARDING);
            case DELAYED -> Optional.of(FlightStatus.BOARDING);
            case BOARDING -> Optional.of(FlightStatus.GATE_CLOSED);
            case GATE_CLOSED -> Optional.of(FlightStatus.DEPARTED);
            default -> Optional.empty(); // departed/cancelled etc: nothing more to do
        };
    }
}
