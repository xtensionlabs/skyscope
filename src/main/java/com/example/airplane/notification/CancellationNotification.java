package com.example.airplane.notification;

import com.example.airplane.model.Flight;

/** A flight has been cancelled. Shown to everyone because passengers must act on it. */
public class CancellationNotification extends Notification {
    public CancellationNotification(Flight flight) { super(flight); }

    @Override public String heading() { return "CANCELLED  ·  " + flight().displayNumber(); }
    @Override public String body() { return flight().status().announcement(flight()); }
    @Override public boolean isBroadcast() { return true; }
}
