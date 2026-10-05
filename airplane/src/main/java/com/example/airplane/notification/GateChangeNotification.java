package com.example.airplane.notification;

import com.example.airplane.model.Flight;

/** A flight moved to a different gate. Shown to everyone, like a real terminal announcement. */
public class GateChangeNotification extends Notification {
    private final String oldGate; // previous gate, so the message can say "was X"

    public GateChangeNotification(Flight flight, String oldGate) {
        super(flight);
        this.oldGate = oldGate;
    }

    @Override public String heading() { return "GATE CHANGE  ·  " + flight().displayNumber(); }
    // Builds e.g. "Nairobi now departs from Gate A2 (was A1)".
    @Override public String body() {
        return flight().destination() + " now departs from Gate " + flight().gate() + " (was " + oldGate + ")";
    }
    @Override public boolean isBroadcast() { return true; } // everyone should see gate changes
}
