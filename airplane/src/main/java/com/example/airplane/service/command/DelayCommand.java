package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/** Delays a flight by a number of minutes. Undo is inherited from FlightCommand. */
public class DelayCommand extends FlightCommand {
    private final int minutes; // how long to delay

    public DelayCommand(String flightId, int minutes) {
        super(flightId);
        this.minutes = minutes;
    }

    // The specific action: delay through the service.
    @Override
    protected void apply(FlightService service, Flight f) throws FidsException { service.delay(f, minutes); }

    // Log text.
    @Override
    public String describe() { return "Delay " + flight.displayNumber() + " by " + minutes + " min"; }
}
