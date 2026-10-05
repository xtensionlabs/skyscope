package com.example.airplane.model;

import java.io.IOException;
import java.util.List;

/**
 * Where flights and bookings come from and go to. Implemented by {@link FlightRepository} (in memory) and
 * {@code JsonDataSource} (file), so the rest of the app never knows which one it is using.
 * OOP: interface + polymorphism - any class that implements load() and save() can be plugged in.
 */
public interface FlightDataSource {
    /** Everything the app needs at start-up. (A record that bundles the two lists together) */
    record Snapshot(List<Flight> flights, List<Booking> bookings) { }

    // Reads all data; IOException is declared because reading a file can fail
    Snapshot load() throws IOException;

    // Stores all data; may also fail with IOException
    void save(Snapshot snapshot) throws IOException;
}
