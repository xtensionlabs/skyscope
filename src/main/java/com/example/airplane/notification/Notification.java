package com.example.airplane.notification;

import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;
import com.example.airplane.service.FlightEvent;

import java.util.Optional;

/** Abstract announcement shown in the banner. Subclasses decide their own wording and who should see them. */
public abstract class Notification {
    private final Flight flight;

    protected Notification(Flight flight) { this.flight = flight; }

    public Flight flight() { return flight; }

    public abstract String heading();

    public abstract String body();

    /** Broadcast notifications are shown to everyone; others only for the pinned flight. */
    public boolean isBroadcast() { return false; }

    /** Factory: turns a flight event into the matching notification, if the event is worth announcing. */
    public static Optional<Notification> from(FlightEvent e) {
        Flight f = e.flight();
        return switch (e.type()) {
            case GATE_CHANGED -> Optional.of(new GateChangeNotification(f, e.oldValue()));
            case DELAYED -> Optional.of(new DelayNotification(f));
            case CANCELLED -> Optional.of(new CancellationNotification(f));
            case STATUS_CHANGED -> f.status() == FlightStatus.BOARDING
                    ? Optional.of(new BoardingCallNotification(f)) : Optional.empty();
            default -> Optional.empty();
        };
    }
}
