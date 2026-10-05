package com.example.airplane.notification;

import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;
import com.example.airplane.service.FlightEvent;

import java.util.Optional;

/**
 * Abstract announcement shown in the banner. Subclasses decide their own wording and who should see them.
 * Abstraction + polymorphism: the UI handles any Notification without knowing its exact subclass.
 */
public abstract class Notification {
    private final Flight flight; // the flight this announcement is about (private = encapsulation)

    protected Notification(Flight flight) { this.flight = flight; }

    // Getter for the flight.
    public Flight flight() { return flight; }

    // Short title of the announcement (each subclass writes its own).
    public abstract String heading();

    // Main message text (each subclass writes its own).
    public abstract String body();

    /** Broadcast notifications are shown to everyone; others only for the pinned flight. */
    public boolean isBroadcast() { return false; } // default: not broadcast; subclasses may override

    /** Factory: turns a flight event into the matching notification, if the event is worth announcing. */
    public static Optional<Notification> from(FlightEvent e) {
        Flight f = e.flight();
        // Choose the subclass according to the event type (Factory Method pattern)
        return switch (e.type()) {
            case GATE_CHANGED -> Optional.of(new GateChangeNotification(f, e.oldValue()));
            case DELAYED -> Optional.of(new DelayNotification(f));
            case CANCELLED -> Optional.of(new CancellationNotification(f));
            // Only announce a status change when it is boarding; other status changes are not announced
            case STATUS_CHANGED -> f.status() == FlightStatus.BOARDING
                    ? Optional.of(new BoardingCallNotification(f)) : Optional.empty();
            default -> Optional.empty(); // other events: no announcement
        };
    }
}
