package com.example.airplane.model;

import com.example.airplane.TestData;
import com.example.airplane.exception.BookingAlreadyCheckedInException;
import com.example.airplane.state.AppState.SortKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelBehaviourTest {
    @Test
    void finalStatusesHaveNoTransitions() {
        assertTrue(FlightStatus.DEPARTED.nextStatuses().isEmpty());
        assertTrue(FlightStatus.CANCELLED.nextStatuses().isEmpty());
        assertTrue(FlightStatus.ON_TIME.nextStatuses().contains(FlightStatus.BOARDING));
        assertFalse(FlightStatus.ON_TIME.nextStatuses().contains(FlightStatus.DEPARTED));
    }

    @Test
    void everyStatusAnnouncesDestination() {
        Flight f = TestData.flight("BA064");
        for (FlightStatus s : FlightStatus.values()) {
            assertTrue(s.announcement(f).contains("London Heathrow") || s.announcement(f).contains("Gate"),
                    s + ": " + s.announcement(f));
        }
    }

    @Test
    void aircraftHierarchyIsPolymorphic() {
        Aircraft narrow = Aircraft.of("A320");
        Aircraft wide = Aircraft.of("B787-8");
        Aircraft regional = Aircraft.of("DHC-8");
        assertInstanceOf(NarrowBody.class, narrow);
        assertInstanceOf(WideBody.class, wide);
        assertInstanceOf(RegionalJet.class, regional);
        assertTrue(wide.cabinCrew() > narrow.cabinCrew());
        assertTrue(narrow.cabinCrew() > regional.cabinCrew());
        assertEquals("Wide-body", wide.category());
        assertTrue(wide.describe().contains("234"));
    }

    @Test
    void unknownAircraftFallsBackToNarrowBody() {
        assertInstanceOf(NarrowBody.class, Aircraft.of("XYZ-1"));
    }

    @Test
    void everyKnownAircraftKeyRoundTrips() {
        for (String key : Aircraft.knownKeys()) assertEquals(key, Aircraft.of(key).key());
    }

    @Test
    void bookingChecksInOnlyOnce() throws Exception {
        Booking b = SampleData.create(java.time.LocalDateTime.now()).bookings().get(0);
        assertFalse(b.isCheckedIn());
        b.checkIn();
        assertTrue(b.isCheckedIn());
        assertThrows(BookingAlreadyCheckedInException.class, b::checkIn);
    }

    @Test
    void sampleDataIsConsistent() {
        FlightDataSource.Snapshot s = SampleData.create(java.time.LocalDateTime.now());
        assertEquals(22, s.flights().size());
        List<String> ids = s.flights().stream().map(Flight::id).toList();
        assertEquals(ids.size(), ids.stream().distinct().count());
        for (Booking b : s.bookings()) assertTrue(ids.contains(b.linkedFlightNumber()), b.bookingReference());
        assertEquals(FlightStatus.values().length,
                s.flights().stream().map(Flight::status).distinct().count());
    }

    @Test
    void sortKeysOrderFlights() {
        List<Flight> flights = new ArrayList<>(SampleData.create(java.time.LocalDateTime.now()).flights());
        flights.sort(SortKey.GATE.comparator());
        assertEquals("A1", flights.get(0).gate());
        assertEquals("C8", flights.get(flights.size() - 1).gate());
        flights.sort(SortKey.STATUS.comparator());
        assertEquals(FlightStatus.ON_TIME, flights.get(0).status());
        assertEquals(FlightStatus.CANCELLED, flights.get(flights.size() - 1).status());
        flights.sort(SortKey.TIME.comparator().reversed());
        assertEquals("KQ610", flights.get(0).id());
    }

    @Test
    void repositoryRemembersWhatWasSaved() {
        FlightRepository repo = new FlightRepository();
        FlightDataSource.Snapshot first = repo.load();
        repo.save(first);
        assertSame(first, repo.load());
    }
}
