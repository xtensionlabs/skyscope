package com.example.airplane.service;

import com.example.airplane.TestData;
import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class StrategyTest {
    /** Random whose nextInt always returns a fixed value, for deterministic tests. */
    private static Random fixed(int value) {
        return new Random() {
            @Override public int nextInt(int bound) { return value; }
        };
    }

    @Test
    void randomStrategyFollowsLifecycle() {
        RandomProgressionStrategy s = new RandomProgressionStrategy();
        LocalDateTime now = LocalDateTime.now();
        assertEquals(Optional.of(FlightStatus.BOARDING), s.next(TestData.flight("BA064"), now, fixed(1)));
        assertEquals(Optional.of(FlightStatus.DELAYED), s.next(TestData.flight("BA064"), now, fixed(0)));
        assertEquals(Optional.of(FlightStatus.BOARDING), s.next(TestData.flight("TK606"), now, fixed(1)));   // delayed
        assertEquals(Optional.of(FlightStatus.GATE_CLOSED), s.next(TestData.flight("EK720"), now, fixed(1))); // boarding
        assertEquals(Optional.of(FlightStatus.DEPARTED), s.next(TestData.flight("ET301"), now, fixed(1)));    // closed
        assertTrue(s.next(TestData.flight("KQ100"), now, fixed(1)).isEmpty());                                // departed
        assertTrue(s.next(TestData.flight("KQ202"), now, fixed(1)).isEmpty());                                // cancelled
    }

    @Test
    void timeAwareWaitsUntilDepartureIsNear() {
        TimeAwareStrategy s = new TimeAwareStrategy();
        Flight far = TestData.flight("KQ004");                      // ~3 hours away, on time
        assertTrue(s.next(far, LocalDateTime.now(), fixed(1)).isEmpty());
        Flight soon = TestData.flight("KQ412");                     // on time
        LocalDateTime twentyMinBefore = soon.estimatedTime().minusMinutes(20);
        assertEquals(Optional.of(FlightStatus.BOARDING), s.next(soon, twentyMinBefore, fixed(1)));
    }

    @Test
    void timeAwareClosesGateThenDeparts() {
        TimeAwareStrategy s = new TimeAwareStrategy();
        Flight boarding = TestData.flight("EK720");
        assertTrue(s.next(boarding, boarding.estimatedTime().minusMinutes(10), fixed(1)).isEmpty());
        assertEquals(Optional.of(FlightStatus.GATE_CLOSED), s.next(boarding, boarding.estimatedTime().minusMinutes(3), fixed(1)));
        Flight closed = TestData.flight("ET301");
        assertEquals(Optional.of(FlightStatus.DEPARTED), s.next(closed, closed.estimatedTime().plusMinutes(1), fixed(1)));
    }

    @Test
    void strategiesHaveDistinctNames() {
        assertNotEquals(new RandomProgressionStrategy().name(), new TimeAwareStrategy().name());
    }
}
