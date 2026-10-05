package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/** Delays a flight by a number of minutes, with a reason. Undo is inherited from FlightCommand. */
public class DelayCommand extends FlightCommand {
    private final int minutes; // how long to delay
    private final String reason; // why the flight is delayed (required)

    public DelayCommand(String flightId, int minutes, String reason) {
        super(flightId);
        this.minutes = minutes;
        this.reason = reason;
    }

    // The specific action: delay through the service.
    @Override
    protected void apply(FlightService service, Flight f) throws FidsException { service.delay(f, minutes, reason); }

    // Log text.
    @Override
    public String describe() { return "Delay " + flight.displayNumber() + " by " + minutes + " min (" + flight.statusReason() + ")"; }
}
