package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/**
 * Base for commands that modify one existing flight. Template method: {@link #execute} looks the flight up and
 * remembers its state (Memento) before calling {@link #apply}, so undo is the same for every subclass.
 */
public abstract class FlightCommand extends AdminCommand {
    protected final String flightId;
    private Flight.Snapshot before;
    protected Flight flight;

    protected FlightCommand(String flightId) { this.flightId = flightId; }

    @Override
    public final void execute(FlightService service) throws FidsException {
        Flight f = service.get(flightId);
        Flight.Snapshot saved = f.snapshot();
        apply(service, f);
        this.flight = f;
        this.before = saved;
    }

    @Override
    public void undo(FlightService service) throws FidsException {
        if (before != null) service.restore(service.get(flightId), before);
    }

    protected abstract void apply(FlightService service, Flight flight) throws FidsException;
}
