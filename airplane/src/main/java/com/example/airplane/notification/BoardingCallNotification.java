package com.example.airplane.notification;

import com.example.airplane.model.Flight;

/** Final boarding call for a flight. Subclass of Notification (inheritance). */
public class BoardingCallNotification extends Notification {
    // Pass the flight up to the parent constructor.
    public BoardingCallNotification(Flight flight) { super(flight); }

    // Overrides the abstract methods with this notification's own wording (polymorphism).
    @Override public String heading() { return "BOARDING  ·  " + flight().displayNumber(); }
    // The status object builds the announcement text for us.
    @Override public String body() { return flight().status().announcement(flight()); }
}
