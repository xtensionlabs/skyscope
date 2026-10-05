package com.example.airplane.service;

import com.example.airplane.exception.*;
import com.example.airplane.model.*;
import com.example.airplane.service.command.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlightServiceTest {
    private FlightService service;
    private final List<FlightEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        service = new FlightService(new FlightRepository());
        service.addListener(events::add);
    }

    @Test
    void lookupByFlightNumberIgnoresCaseAndSpaces() throws Exception {
        assertEquals("BA064", service.lookup("ba 064").id());
        assertEquals("BA064", service.lookup("BA064").id());
    }

    @Test
    void lookupByBookingReference() throws Exception {
        assertEquals("BA064", service.lookup("bk7f3a").id());
    }

    @Test
    void lookupUnknownThrows() {
        assertThrows(FlightNotFoundException.class, () -> service.lookup("nope"));
        assertThrows(FlightNotFoundException.class, () -> service.lookup(""));
        assertThrows(FlightNotFoundException.class, () -> service.lookup(null));
    }

    @Test
    void bookingMatchingPrefersReference() throws Exception {
        Flight f = service.lookup("BK2M9Q");
        assertEquals("KQ610", f.id());
        assertEquals("BK2M9Q", service.bookingMatching("BK2M9Q", f).orElseThrow().bookingReference());
    }

    @Test
    void passengerCanHaveSeveralBookings() throws Exception {
        Booking b = service.bookingMatching("BK7F3A", service.lookup("BK7F3A")).orElseThrow();
        assertEquals(2, service.bookingsOf(b.passenger()).size());
    }

    @Test
    void gateChangeFiresEventWithOldGate() throws Exception {
        Flight f = service.get("KQ412");
        service.changeGate(f, "A4");
        assertEquals("A4", f.gate());
        assertEquals(1, events.size());
        assertEquals(FlightEvent.Type.GATE_CHANGED, events.get(0).type());
        assertEquals("A6", events.get(0).oldValue());
    }

    @Test
    void gateConflictDetected() throws Exception {
        Flight f = service.get("KQ412");      // departs ~30 min from now
        // A3 belongs to ET301, departing ~4 min from now: too close.
        GateOccupiedException e = assertThrows(GateOccupiedException.class, () -> service.changeGate(f, "A3"));
        assertTrue(e.getMessage().contains("A3"));
        assertEquals("A6", f.gate());
        assertTrue(events.isEmpty());
    }

    @Test
    void invalidGateRejected() throws Exception {
        Flight f = service.get("KQ412");
        assertThrows(InvalidGateException.class, () -> service.changeGate(f, "Z9"));
    }

    @Test
    void closedGateCannotMove() throws Exception {
        Flight f = service.get("ET301");
        assertThrows(InvalidFlightException.class, () -> service.changeGate(f, "A8"));
    }

    @Test
    void statusMovesOnlyLegally() throws Exception {
        Flight f = service.get("BA064");
        assertThrows(InvalidFlightException.class, () -> service.moveStatus(f, FlightStatus.DEPARTED));
        service.moveStatus(f, FlightStatus.BOARDING);
        assertEquals(FlightStatus.BOARDING, f.status());
        assertEquals("On Time", events.get(0).oldValue());
    }

    @Test
    void duplicateFlightRejected() throws Exception {
        Flight dupe = new Flight("BA064", SampleData.airline("BA"), "Nairobi", "Rome", "FCO",
                LocalDateTime.now().plusHours(9), "B7", FlightStatus.ON_TIME, "x", "y", Aircraft.of("A320"));
        assertThrows(InvalidFlightException.class, () -> service.addFlight(dupe));
    }

    @Test
    void checkInOnceOnly() throws Exception {
        Booking b = service.checkIn("BK7F3A");
        assertTrue(b.isCheckedIn());
        assertThrows(BookingAlreadyCheckedInException.class, () -> service.checkIn("BK7F3A"));
    }

    @Test
    void checkInClosedAfterGateCloses() throws Exception {
        service.forceStatus(service.get("KL564"), FlightStatus.GATE_CLOSED);
        assertThrows(InvalidFlightException.class, () -> service.checkIn("RT55HD"));
    }

    @Test
    void checkInUnknownReference() {
        assertThrows(FlightNotFoundException.class, () -> service.checkIn("ZZZZZZ"));
    }

    @Test
    void commandsExecuteAreAuditedAndUndoable() throws Exception {
        service.execute(new ChangeGateCommand("KQ412", "A4"), "STAFF");
        service.execute(new DelayCommand("KQ412", 40, "Test reason"), "STAFF");
        Flight f = service.get("KQ412");
        assertEquals("A4", f.gate());
        assertEquals(FlightStatus.DELAYED, f.status());
        assertEquals(2, service.auditLog().size());
        assertTrue(service.auditLog().get(0).description().contains("Delay"));   // newest first

        service.undoLast("STAFF");
        assertEquals(FlightStatus.ON_TIME, f.status());
        assertEquals("A4", f.gate());
        service.undoLast("STAFF");
        assertEquals("A6", f.gate());
        assertFalse(service.canUndo());
        assertTrue(service.undoLast("STAFF").isEmpty());
    }

    @Test
    void failedCommandIsNotRecorded() {
        assertThrows(InvalidGateException.class,
                () -> service.execute(new ChangeGateCommand("KQ412", "Q1"), "STAFF"));
        assertFalse(service.canUndo());
        assertTrue(service.auditLog().isEmpty());
    }

    @Test
    void cancelCommandUndo() throws Exception {
        service.execute(new CancelCommand("BA064", "Strike"), "STAFF");
        assertEquals(FlightStatus.CANCELLED, service.get("BA064").status());
        service.undoLast("STAFF");
        assertEquals(FlightStatus.ON_TIME, service.get("BA064").status());
        assertTrue(service.get("BA064").statusReason().isBlank());
    }

    @Test
    void addFlightCommandUndoRemoves() throws Exception {
        Flight f = new Flight("KQ777", SampleData.airline("KQ"), "Nairobi", "Cairo", "CAI",
                LocalDateTime.now().plusHours(6), "C4", FlightStatus.ON_TIME, "x", "y", Aircraft.of("B737-800"));
        int before = service.flights().size();
        service.execute(new AddFlightCommand(f), "STAFF");
        assertEquals(before + 1, service.flights().size());
        service.undoLast("STAFF");
        assertEquals(before, service.flights().size());
        assertTrue(service.find("KQ777").isEmpty());
    }

    @Test
    void unknownFlightInCommand() {
        assertThrows(FlightNotFoundException.class,
                () -> service.execute(new DelayCommand("XX999", 10, "Test reason"), "STAFF"));
    }
}
