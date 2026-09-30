package com.example.airplane.model;

import java.time.LocalDateTime;

/** A departing flight. Status, gate and estimate are mutable (changed by the live simulation). */
public class Flight {
    private final String flightNumber;
    private final Airline airline;
    private final String origin;
    private final String destination;
    private final String destinationCode;
    private final LocalDateTime scheduledTime;
    private final String terminal;
    private final String checkInCounter;
    private final String baggageBelt;
    private String gate;
    private FlightStatus status;
    private LocalDateTime estimatedTime;

    public Flight(String flightNumber, Airline airline, String origin, String destination, String destinationCode,
                  LocalDateTime scheduledTime, String gate, String terminal, FlightStatus status,
                  String checkInCounter, String baggageBelt) {
        this.flightNumber = flightNumber;
        this.airline = airline;
        this.origin = origin;
        this.destination = destination;
        this.destinationCode = destinationCode;
        this.scheduledTime = scheduledTime;
        this.estimatedTime = scheduledTime;
        this.gate = gate;
        this.terminal = terminal;
        this.status = status;
        this.checkInCounter = checkInCounter;
        this.baggageBelt = baggageBelt;
    }

    /** Unique id, e.g. "KQ100". */
    public String id() { return flightNumber; }
    public String flightNumber() { return flightNumber; }
    /** "KQ 100" for display. */
    public String displayNumber() { return flightNumber.substring(0, 2) + " " + flightNumber.substring(2); }
    public Airline airline() { return airline; }
    public String origin() { return origin; }
    public String destination() { return destination; }
    public String destinationCode() { return destinationCode; }
    public LocalDateTime scheduledTime() { return scheduledTime; }
    public LocalDateTime estimatedTime() { return estimatedTime; }
    public String gate() { return gate; }
    public String terminal() { return terminal; }
    public FlightStatus status() { return status; }
    public String checkInCounter() { return checkInCounter; }
    public String baggageBelt() { return baggageBelt; }

    public void setGate(String gate) { this.gate = gate; }
    public void setStatus(FlightStatus status) { this.status = status; }
    public void setEstimatedTime(LocalDateTime t) { this.estimatedTime = t; }
}
