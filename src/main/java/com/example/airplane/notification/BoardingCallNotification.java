package com.example.airplane.notification;

import com.example.airplane.model.Flight;

/** Final boarding call for a flight. */
public class BoardingCallNotification extends Notification {
    public BoardingCallNotification(Flight flight) { super(flight); }

    @Override public String heading() { return "BOARDING  ·  " + flight().displayNumber(); }
    @Override public String body() { return flight().status().announcement(flight()); }
}
