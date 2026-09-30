package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/** Delays a flight by a number of minutes. */
public class DelayCommand extends FlightCommand {
    private final int minutes;

    public DelayCommand(String flightId, int minutes) {
        super(flightId);
        this.minutes = minutes;
    }

    @Override
    protected void apply(FlightService service, Flight f) throws FidsException { service.delay(f, minutes); }

    @Override
    public String describe() { return "Delay " + flight.displayNumber() + " by " + minutes + " min"; }
}
