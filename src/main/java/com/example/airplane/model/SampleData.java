package com.example.airplane.model;

import com.example.airplane.exception.FidsException;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Demo data loaded from the resource file data/seed.json (no flights are typed into the code any more).
 * Departure times in the file are "minutes from now", so the board always looks live.
 */
public final class SampleData {   // final: cannot be extended; only static helpers (utility class)
    // File shapes: plain records that Gson fills in from seed.json (field names match the JSON keys)
    private record FlightRow(String flightNumber, String destination, String destinationCode, int minutesFromNow,
                             String gate, String status, String checkIn, int belt, String aircraft) { }
    private record BookingRow(String reference, String passengerId, String passengerName, String flight,
                              String seat, String group) { }
    /** A place a flight can go to: city name and 3-letter airport code (used for the staff drop-down). */
    public record Destination(String city, String code) { }
    private record SeedFile(List<Airline> airlines, List<Destination> destinations, List<FlightRow> flights,
                            List<BookingRow> bookings) { }

    // The whole seed file, read once when the class is first used
    private static final SeedFile SEED = readSeed();
    // Map from airline code to Airline; LinkedHashMap keeps the order used in the file
    private static final Map<String, Airline> AIRLINES = new LinkedHashMap<>();
    static {
        for (Airline a : SEED.airlines()) AIRLINES.put(a.code(), a);
    }

    // Reads and parses data/seed.json from the classpath; the app cannot run without it, so fail loudly
    private static SeedFile readSeed() {
        try (InputStream in = SampleData.class.getResourceAsStream("/data/seed.json")) {
            if (in == null) throw new IllegalStateException("Missing resource /data/seed.json");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SeedFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read /data/seed.json", e);
        }
    }

    // Returns a read-only copy so outside code cannot change our map (encapsulation)
    public static List<Airline> airlines() { return List.copyOf(AIRLINES.values()); }

    // Read-only list of destinations for the staff drop-down
    public static List<Destination> destinations() { return List.copyOf(SEED.destinations()); }

    // Check-in desk ranges already used in the seed data, sorted (stream: distinct + sorted)
    public static List<String> checkInDesks() {
        return SEED.flights().stream().map(FlightRow::checkIn).distinct().sorted().toList();
    }

    // Baggage belts already used in the seed data, e.g. "Belt 1"
    public static List<String> baggageBelts() {
        return SEED.flights().stream().map(r -> "Belt " + r.belt()).distinct().sorted().toList();
    }

    /** Looks up an airline by IATA code; unknown codes get a neutral grey placeholder. */
    public static Airline airline(String code) {
        return AIRLINES.getOrDefault(code, new Airline(code, code, "#555555"));
    }

    /** Builds the full demo data set (flights + bookings) relative to the given time. */
    public static FlightDataSource.Snapshot create(LocalDateTime now) {
        // Drop seconds so times look neat
        LocalDateTime base = now.truncatedTo(ChronoUnit.MINUTES);
        List<Flight> flights = new ArrayList<>();
        for (FlightRow r : SEED.flights()) {
            try {
                // Airline code = first 2 letters of the flight number; origin is always Nairobi;
                // Aircraft.of(...) is the factory method that picks the right Aircraft subclass
                Flight f = new Flight(r.flightNumber(), airline(r.flightNumber().substring(0, 2)), "Nairobi",
                        r.destination(), r.destinationCode(), base.plusMinutes(r.minutesFromNow()), r.gate(),
                        FlightStatus.valueOf(r.status()), r.checkIn(), "Belt " + r.belt(), Aircraft.of(r.aircraft()));
                // Delayed / cancelled flights carry the matching extra data
                if (f.status() == FlightStatus.DELAYED) {
                    // Delayed: estimated time is 35 minutes after the schedule
                    f.restore(new Flight.Snapshot(f.gate(), FlightStatus.DELAYED, f.scheduledTime().plusMinutes(35),
                            "Late arrival of inbound aircraft."));
                } else if (f.status() == FlightStatus.CANCELLED) {
                    // Cancelled: give it a reason text
                    f.restore(new Flight.Snapshot(f.gate(), FlightStatus.CANCELLED, f.scheduledTime(),
                            "Aircraft unavailable."));
                }
                flights.add(f);
            } catch (FidsException e) {
                // Exception handling: our own demo data should never be invalid, so crash loudly if it is
                throw new IllegalStateException("Bad sample data for " + r.flightNumber() + ": " + e.getMessage(), e);
            }
        }

        // One Passenger object per id, so a passenger with several bookings is shared (one passenger, many bookings)
        Map<String, Passenger> people = new HashMap<>();
        List<Booking> bookings = new ArrayList<>();
        for (BookingRow r : SEED.bookings()) {
            Passenger p = people.computeIfAbsent(r.passengerId(), id -> new Passenger(id, r.passengerName()));
            // Demo bookings start not checked in
            bookings.add(new Booking(r.reference(), p, r.flight(), r.seat(), r.group(), false));
        }
        // Bundle both lists into one Snapshot record and return it
        return new FlightDataSource.Snapshot(flights, bookings);
    }

    // Private constructor: nobody can create a SampleData object (it is used only through static methods)
    private SampleData() { }
}
