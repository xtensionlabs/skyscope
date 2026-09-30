package com.example.airplane.notification;

import com.example.airplane.model.Flight;

/** A flight has been delayed. Uses the status's own announcement text. */
public class DelayNotification extends Notification {
    public DelayNotification(Flight flight) { super(flight); }

    @Override public String heading() { return "DELAY  ·  " + flight().displayNumber(); }
    @Override public String body() { return flight().status().announcement(flight()); }
}
