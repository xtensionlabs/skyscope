package com.example.airplane.model;

import com.example.airplane.TestData;
import com.example.airplane.exception.InvalidFlightException;
import com.example.airplane.exception.InvalidGateException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FlightTest {
    private static Flight make(String no, String gate) throws Exception {
        return new Flight(no, SampleData.airline("KQ"), "Nairobi", "Cairo", "CAI", LocalDateTime.now().plusHours(2),
                gate, FlightStatus.ON_TIME, "A 1-5", "Belt 1", Aircraft.of("A320"));
    }

    @Test
    void rejectsBadFlightNumber() {
        assertThrows(InvalidFlightException.class, () -> make("K", "A1"));
        assertThrows(InvalidFlightException.class, () -> make("kq100", "A1"));
        assertThrows(InvalidFlightException.class, () -> make("KQ12345", "A1"));
    }

    @Test
    void rejectsBadGate() {
        assertThrows(InvalidGateException.class, () -> make("KQ777", "Z9"));
        assertThrows(InvalidGateException.class, () -> make("KQ777", "A9"));
        assertThrows(InvalidGateException.class, () -> make("KQ777", null));
    }

    @Test
    void rejectsBadDestinationCode() {
        assertThrows(InvalidFlightException.class, () -> new Flight("KQ777", SampleData.airline("KQ"), "Nairobi",
                "Cairo", "CAIRO", LocalDateTime.now(), "A1", FlightStatus.ON_TIME, "x", "y", Aircraft.of("A320")));
    }

    @Test
    void terminalFollowsGatePier() throws Exception {
        assertEquals("Terminal 1", make("KQ777", "A3").terminal());
        Flight f = make("KQ778", "B2");
        assertEquals("Terminal 2", f.terminal());
        f.changeGate("C4");
        assertEquals("Terminal 3", f.terminal());
    }

    @Test
    void displayNumberHasSpace() throws Exception {
        assertEquals("KQ 777", make("KQ777", "A1").displayNumber());
    }

    @Test
    void changeGateReturnsOldGateAndRejectsSameGate() throws Exception {
        Flight f = make("KQ777", "A1");
        assertEquals("A1", f.changeGate("A2"));
        assertEquals("A2", f.gate());
        assertThrows(InvalidFlightException.class, () -> f.changeGate("A2"));
    }

    @Test
    void gateCannotChangeOnceClosed() {
        Flight f = TestData.flight("ET301"); // Gate Closed
        assertFalse(f.canChangeGate());
        assertThrows(InvalidFlightException.class, () -> f.changeGate("A8"));
    }

    @Test
    void delayMovesEstimateAndStatus() throws Exception {
        Flight f = make("KQ777", "A1");
        LocalDateTime before = f.estimatedTime();
        f.delayByMinutes(30, "Test reason");
        assertEquals(FlightStatus.DELAYED, f.status());
        assertEquals(before.plusMinutes(30), f.estimatedTime());
        f.delayByMinutes(15, "Test reason");
        assertEquals(before.plusMinutes(45), f.estimatedTime());
    }

    @Test
    void delayValidation() throws Exception {
        Flight f = make("KQ777", "A1");
        assertThrows(InvalidFlightException.class, () -> f.delayByMinutes(0, "Test reason"));
        assertThrows(InvalidFlightException.class, () -> f.delayByMinutes(5000, "Test reason"));
        // A reason is required
        assertThrows(InvalidFlightException.class, () -> f.delayByMinutes(10, " "));
        assertThrows(InvalidFlightException.class, () -> f.delayByMinutes(10, null));
        // A boarding flight can still be delayed, and the reason is stored
        Flight boarding = TestData.flight("EK720");
        boarding.delayByMinutes(10, "Bad weather");
        assertEquals(FlightStatus.DELAYED, boarding.status());
        assertEquals("Bad weather", boarding.statusReason());
        // Once the gate has closed it is too late to delay
        Flight closed = TestData.flight("ET301");
        assertThrows(InvalidFlightException.class, () -> closed.delayByMinutes(10, "Test reason"));
    }

    @Test
    void moveToOnlyAlongLegalTransitions() throws Exception {
        Flight f = make("KQ777", "A1");
        assertThrows(InvalidFlightException.class, () -> f.moveTo(FlightStatus.DEPARTED));
        f.moveTo(FlightStatus.BOARDING);
        f.moveTo(FlightStatus.GATE_CLOSED);
        f.moveTo(FlightStatus.DEPARTED);
        assertFalse(f.status().isActive());
        assertThrows(InvalidFlightException.class, () -> f.moveTo(FlightStatus.BOARDING));
    }

    @Test
    void cancelOnceWithReason() throws Exception {
        Flight f = make("KQ777", "A1");
        f.cancel("Storm");
        assertEquals(FlightStatus.CANCELLED, f.status());
        assertEquals("Storm", f.statusReason());
        assertThrows(InvalidFlightException.class, () -> f.cancel("again"));
    }

    @Test
    void cancelWithoutReasonGetsDefault() throws Exception {
        Flight f = make("KQ777", "A1");
        f.cancel("  ");
        assertFalse(f.statusReason().isBlank());
    }

    @Test
    void boardableOnlyWhileBoarding() {
        assertTrue(TestData.flight("EK720").isBoardable());
        assertFalse(TestData.flight("BA064").isBoardable());
    }

    @Test
    void snapshotAndRestoreRoundTrip() throws Exception {
        Flight f = make("KQ777", "A1");
        Flight.Snapshot before = f.snapshot();
        f.changeGate("A2");
        f.delayByMinutes(20, "Test reason");
        f.restore(before);
        assertEquals("A1", f.gate());
        assertEquals(FlightStatus.ON_TIME, f.status());
        assertEquals(before.estimatedTime(), f.estimatedTime());
    }

    @Test
    void gateSortKeyPadsNumbers() throws Exception {
        assertEquals("B02", make("KQ777", "B2").gateSortKey());
    }
}
