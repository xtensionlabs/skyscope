package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;
import com.example.airplane.service.FlightService;

/** Staff override: forces any status, ignoring the normal transition rules. Undo is inherited. */
public class SetStatusCommand extends FlightCommand {
    private final FlightStatus status; // the status to force

    public SetStatusCommand(String flightId, FlightStatus status) {
        super(flightId);
        this.status = status;
    }

    // The specific action: force the status (no legality check).
    @Override
    protected void apply(FlightService service, Flight f) throws FidsException { service.forceStatus(f, status); }

    // Log text.
    @Override
    public String describe() { return "Set status " + flight.displayNumber() + " -> " + status.label(); }
}
