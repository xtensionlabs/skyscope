package com.example.airplane;

import com.example.airplane.model.Flight;
import com.example.airplane.model.SampleData;

import java.time.LocalDateTime;

/** Shared helpers for tests. */
public final class TestData {
    /** Fresh copy of the sample flights (so tests can mutate them freely). */
    public static Flight flight(String id) {
        return SampleData.create(LocalDateTime.now()).flights().stream()
                .filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    private TestData() { }
}
