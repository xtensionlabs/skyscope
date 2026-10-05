package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/** Cancels a flight with a reason. Undo is inherited from FlightCommand. */
public class CancelCommand extends FlightCommand {
    private final String reason; // why the flight is cancelled

    public CancelCommand(String flightId, String reason) {
        super(flightId); // pass the id up to the parent constructor
        this.reason = reason;
    }

    // The specific action for this command: cancel through the service.
    @Override
    protected void apply(FlightService service, Flight f) throws FidsException { service.cancel(f, reason); }

    // Log text; uses the flight's stored cancellation reason.
    @Override
    public String describe() { return "Cancel " + flight.displayNumber() + " (" + flight.statusReason() + ")"; }
}
