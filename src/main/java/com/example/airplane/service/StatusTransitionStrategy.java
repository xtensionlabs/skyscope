package com.example.airplane.service;

import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/** Strategy deciding how a flight's status should progress next (empty = nothing to do yet). */
public interface StatusTransitionStrategy {
    String name();

    Optional<FlightStatus> next(Flight flight, LocalDateTime now, Random rnd);
}
