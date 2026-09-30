package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/** Moves a flight to another gate. */
public class ChangeGateCommand extends FlightCommand {
    private final String newGate;
    private String oldGate;

    public ChangeGateCommand(String flightId, String newGate) {
        super(flightId);
        this.newGate = newGate;
    }

    @Override
    protected void apply(FlightService service, Flight f) throws FidsException {
        oldGate = service.changeGate(f, newGate);
    }

    @Override
    public String describe() { return "Gate change " + flight.displayNumber() + ": " + oldGate + " -> " + newGate; }
}
