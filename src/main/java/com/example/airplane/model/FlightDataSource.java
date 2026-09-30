package com.example.airplane.model;

import java.io.IOException;
import java.util.List;

/**
 * Where flights and bookings come from and go to. Implemented by {@link FlightRepository} (in memory) and
 * {@code JsonDataSource} (file), so the rest of the app never knows which one it is using.
 */
public interface FlightDataSource {
    /** Everything the app needs at start-up. */
    record Snapshot(List<Flight> flights, List<Booking> bookings) { }

    Snapshot load() throws IOException;

    void save(Snapshot snapshot) throws IOException;
}
