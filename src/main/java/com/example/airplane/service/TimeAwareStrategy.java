package com.example.airplane.service;

import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/** Realistic mode: a flight only changes status when its departure time says it should. */
public class TimeAwareStrategy implements StatusTransitionStrategy {
    @Override public String name() { return "Realistic (follows the clock)"; }

    @Override
    public Optional<FlightStatus> next(Flight f, LocalDateTime now, Random rnd) {
        long minutes = f.minutesToDeparture(now);
        return switch (f.status()) {
            case ON_TIME -> {
                if (minutes <= 25) yield Optional.of(FlightStatus.BOARDING);
                yield rnd.nextInt(40) == 0 ? Optional.of(FlightStatus.DELAYED) : Optional.empty();
            }
            case DELAYED -> minutes <= 25 ? Optional.of(FlightStatus.BOARDING) : Optional.empty();
            case BOARDING -> minutes <= 5 ? Optional.of(FlightStatus.GATE_CLOSED) : Optional.empty();
            case GATE_CLOSED -> minutes <= 0 ? Optional.of(FlightStatus.DEPARTED) : Optional.empty();
            default -> Optional.empty();
        };
    }
}
