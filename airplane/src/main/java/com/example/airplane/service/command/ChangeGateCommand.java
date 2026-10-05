package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/** Moves a flight to another gate. Undo is inherited from FlightCommand. */
public class ChangeGateCommand extends FlightCommand {
    private final String newGate; // gate to move to
    private String oldGate;       // gate it was at, filled in when the command runs

    public ChangeGateCommand(String flightId, String newGate) {
        super(flightId);
        this.newGate = newGate;
    }

    // The specific action: change the gate and remember the previous one for the log.
    @Override
    protected void apply(FlightService service, Flight f) throws FidsException {
        oldGate = service.changeGate(f, newGate);
    }

    // Log text, e.g. "Gate change KQ100: A1 -> A2".
    @Override
    public String describe() { return "Gate change " + flight.displayNumber() + ": " + oldGate + " -> " + newGate; }
}
