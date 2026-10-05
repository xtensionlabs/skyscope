package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/**
 * Adds a new flight to the board; undo removes it again.
 * Extends AdminCommand directly because the flight does not exist yet (inheritance).
 */
public class AddFlightCommand extends AdminCommand {
    private final Flight flight; // the new flight to add

    public AddFlightCommand(Flight flight) { this.flight = flight; }

    // Do: ask the service to add the flight (it may reject it, e.g. duplicate id or gate clash).
    @Override
    public void execute(FlightService service) throws FidsException { service.addFlight(flight); }

    // Undo: take the flight out again.
    @Override
    public void undo(FlightService service) { service.removeFlight(flight); }

    // Text for the audit log.
    @Override
    public String describe() {
        return "Add flight " + flight.displayNumber() + " to " + flight.destination() + " (gate " + flight.gate() + ")";
    }
}
