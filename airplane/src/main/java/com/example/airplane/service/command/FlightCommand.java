package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.service.FlightService;

/**
 * Base for commands that modify one existing flight. Template method: {@link #execute} looks the flight up and
 * remembers its state (Memento) before calling {@link #apply}, so undo is the same for every subclass.
 */
public abstract class FlightCommand extends AdminCommand {
    protected final String flightId;  // which flight to change (subclasses can read it)
    private Flight.Snapshot before;   // saved copy of the flight's state, used by undo (Memento)
    protected Flight flight;          // the flight, set after a successful execute

    protected FlightCommand(String flightId) { this.flightId = flightId; }

    // final: subclasses cannot change these steps; they only supply apply() (Template Method pattern).
    @Override
    public final void execute(FlightService service) throws FidsException {
        Flight f = service.get(flightId);        // 1. find the flight
        Flight.Snapshot saved = f.snapshot();    // 2. save its current state
        apply(service, f);                       // 3. do the specific change (differs per subclass)
        // Only store these if apply() did not throw, so a failed command leaves nothing to undo
        this.flight = f;
        this.before = saved;
    }

    // Undo for all flight commands: put the flight back to the saved snapshot.
    @Override
    public void undo(FlightService service) throws FidsException {
        if (before != null) service.restore(service.get(flightId), before);
    }

    // The one step each subclass must provide: the actual change.
    protected abstract void apply(FlightService service, Flight flight) throws FidsException;
}
