package com.example.airplane.model;

import java.time.LocalDateTime;

/** In-memory data source seeded with {@link SampleData}. Nothing is written to disk. */
public class FlightRepository implements FlightDataSource {
    private Snapshot lastSaved;

    @Override
    public Snapshot load() {
        return lastSaved != null ? lastSaved : SampleData.create(LocalDateTime.now());
    }

    @Override
    public void save(Snapshot snapshot) {
        lastSaved = snapshot;
    }
}
