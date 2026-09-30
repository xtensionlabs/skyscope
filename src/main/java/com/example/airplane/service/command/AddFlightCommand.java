package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/** Adds a new flight to the board; undo removes it again. */
public class AddFlightCommand extends AdminCommand {
    private final Flight flight;

    public AddFlightCommand(Flight flight) { this.flight = flight; }

    @Override
    public void execute(FlightService service) throws FidsException { service.addFlight(flight); }

    @Override
    public void undo(FlightService service) { service.removeFlight(flight); }

    @Override
    public String describe() {
        return "Add flight " + flight.displayNumber() + " to " + flight.destination() + " (gate " + flight.gate() + ")";
    }
}
