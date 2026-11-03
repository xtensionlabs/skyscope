package com.example.airplane.model;

import java.io.IOException;
import java.util.List;

/**
 * Where flights and bookings come from and go to. The rest of the app depends on this interface,
 * so storage can be changed without changing its business rules or screens.
 * OOP: interface + polymorphism - any class that implements load() and save() can be plugged in.
 */
public interface FlightDataSource {
    /** Everything the app needs at start-up, including the reference lists used by the staff form. */
    record Snapshot(List<Flight> flights, List<Booking> bookings, List<Airline> airlines,
                    List<SampleData.Destination> destinations, List<String> checkInDesks,
                    List<String> baggageBelts) {
        public Snapshot {
            flights = List.copyOf(flights);
            bookings = List.copyOf(bookings);
            airlines = List.copyOf(airlines);
            destinations = List.copyOf(destinations);
            checkInDesks = List.copyOf(checkInDesks);
            baggageBelts = List.copyOf(baggageBelts);
        }

        /** Keeps the simple constructor for in-memory and JSON examples. */
        public Snapshot(List<Flight> flights, List<Booking> bookings) {
            this(flights, bookings, SampleData.airlines(), SampleData.destinations(),
                    SampleData.checkInDesks(), SampleData.baggageBelts());
        }
    }

    // Reads all data; IOException is declared because a storage operation can fail
    Snapshot load() throws IOException;

    // Stores all data; may also fail with IOException
    void save(Snapshot snapshot) throws IOException;
}
