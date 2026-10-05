package com.example.airplane.model;

import java.time.LocalDateTime;

/**
 * In-memory data source seeded with {@link SampleData}. Nothing is written to disk.
 * OOP: implements the FlightDataSource interface.
 */
public class FlightRepository implements FlightDataSource {
    // Remembers the most recent data given to save(); null until save() is called
    private Snapshot lastSaved;

    // If something was saved earlier return it, otherwise create fresh sample data (ternary: condition ? a : b)
    @Override
    public Snapshot load() {
        return lastSaved != null ? lastSaved : SampleData.create(LocalDateTime.now());
    }

    // "Saving" only keeps the data in this object's memory
    @Override
    public void save(Snapshot snapshot) {
        lastSaved = snapshot;
    }
}
