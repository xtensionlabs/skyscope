package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;
import com.example.airplane.service.FlightService;

/** Staff override: forces any status, ignoring the normal transition rules. */
public class SetStatusCommand extends FlightCommand {
    private final FlightStatus status;

    public SetStatusCommand(String flightId, FlightStatus status) {
        super(flightId);
        this.status = status;
    }

    @Override
    protected void apply(FlightService service, Flight f) throws FidsException { service.forceStatus(f, status); }

    @Override
    public String describe() { return "Set status " + flight.displayNumber() + " -> " + status.label(); }
}
