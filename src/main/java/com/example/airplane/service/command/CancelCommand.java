package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/** Cancels a flight with a reason. */
public class CancelCommand extends FlightCommand {
    private final String reason;

    public CancelCommand(String flightId, String reason) {
        super(flightId);
        this.reason = reason;
    }

    @Override
    protected void apply(FlightService service, Flight f) throws FidsException { service.cancel(f, reason); }

    @Override
    public String describe() { return "Cancel " + flight.displayNumber() + " (" + flight.cancellationReason() + ")"; }
}
