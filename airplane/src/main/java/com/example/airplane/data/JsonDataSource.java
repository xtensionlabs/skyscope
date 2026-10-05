package com.example.airplane.data;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Persists flights and bookings as a JSON file. On first run the file is created from the sample data.
 * Because departure times are absolute, loading shifts every time by the gap since the file was saved, so the
 * board always resumes exactly where it was left instead of showing yesterday's flights.
 * OOP: implements the FlightDataSource interface (polymorphism - the app can use this or the in-memory version).
 */
public class JsonDataSource implements FlightDataSource {
    // Records below are plain "file shapes": simple copies of our objects that Gson can turn to/from JSON text.
    // Times are stored as Strings and the status/airline/aircraft as short codes.
    record FlightRec(String flightNumber, String airline, String origin, String destination, String destinationCode,
                     String scheduled, String estimated, String gate, String status, String checkIn, String belt,
                     String aircraft, String cancelReason) { }

    // File form of a Booking (passenger stored as id + name)
    record BookingRec(String reference, String passengerId, String passengerName, String flight, String seat,
                      String group, boolean checkedIn) { }

    // Whole file: when it was saved, plus all flights and bookings
    record FileRec(String savedAt, List<FlightRec> flights, List<BookingRec> bookings) { }

    private final Path file;   // where the JSON file lives on disk
    // Gson converts objects to JSON and back; pretty printing makes the file easy to read
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Constructor: remember which file to use
    public JsonDataSource(Path file) { this.file = file; }

    // Reads the file and rebuilds Flight and Booking objects (IOException if reading/parsing fails)
    @Override
    public Snapshot load() throws IOException {
        // First run: no file yet, so create it from the sample data and use that
        if (!Files.exists(file)) {
            Snapshot seeded = SampleData.create(LocalDateTime.now());
            save(seeded);
            return seeded;
        }
        // try-with-resources: the Reader is closed automatically when the block ends
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            // Turn the JSON text into a FileRec object
            FileRec rec = gson.fromJson(in, FileRec.class);
            // Reject missing or incomplete data
            if (rec == null || rec.flights() == null || rec.bookings() == null || rec.savedAt() == null) {
                throw new IOException("Data file is empty or incomplete: " + file);
            }
            // How much time has passed since the file was saved; all times are moved forward by this
            Duration shift = Duration.between(LocalDateTime.parse(rec.savedAt()), LocalDateTime.now());
            // Convert every saved flight record back into a Flight object
            List<Flight> flights = new ArrayList<>();
            for (FlightRec r : rec.flights()) flights.add(toFlight(r, shift));
            // Map of passenger id -> Passenger so a passenger with many bookings is only created once
            Map<String, Passenger> people = new HashMap<>();
            List<Booking> bookings = new ArrayList<>();
            for (BookingRec r : rec.bookings()) {
                // computeIfAbsent: reuse the passenger if known, otherwise create it (lambda expression)
                Passenger p = people.computeIfAbsent(r.passengerId(), id -> new Passenger(id, r.passengerName()));
                bookings.add(new Booking(r.reference(), p, r.flight(), r.seat(), r.group(), r.checkedIn()));
            }
            return new Snapshot(flights, bookings);
        } catch (JsonParseException | IllegalArgumentException | java.time.DateTimeException e) {
            // Exception handling: bad JSON, bad enum name or bad date all become one friendly IOException
            throw new IOException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    // Writes all flights and bookings to the JSON file
    @Override
    public void save(Snapshot s) throws IOException {
        // Stream + method reference: turn each Flight into a FlightRec
        List<FlightRec> flights = s.flights().stream().map(JsonDataSource::toRec).toList();
        // Stream + lambda: turn each Booking into a BookingRec
        List<BookingRec> bookings = s.bookings().stream().map(b -> new BookingRec(b.bookingReference(),
                b.passenger().id(), b.passenger().name(), b.linkedFlightNumber(), b.seat(), b.boardingGroup(),
                b.isCheckedIn())).toList();
        // Record the save time, so load() knows how far to shift the times
        FileRec rec = new FileRec(LocalDateTime.now().toString(), flights, bookings);
        // Make sure the folder exists before writing the file
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        // try-with-resources closes the Writer automatically
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(rec, out);
        }
    }

    // Converts a Flight into its file form (enum -> name(), aircraft -> key, times -> text)
    private static FlightRec toRec(Flight f) {
        return new FlightRec(f.flightNumber(), f.airline().code(), f.origin(), f.destination(), f.destinationCode(),
                f.scheduledTime().toString(), f.estimatedTime().toString(), f.gate(), f.status().name(),
                f.checkInCounter(), f.baggageBelt(), f.aircraft().key(), f.cancellationReason());
    }

    // Converts a file record back into a Flight, moving its times forward by "shift"
    private static Flight toFlight(FlightRec r, Duration shift) throws IOException {
        try {
            LocalDateTime scheduled = LocalDateTime.parse(r.scheduled()).plus(shift);
            // Rebuild using the constructor (so all validation rules run again);
            // valueOf turns the saved text back into a FlightStatus enum constant
            Flight f = new Flight(r.flightNumber(), SampleData.airline(r.airline()), r.origin(), r.destination(),
                    r.destinationCode(), scheduled, r.gate(), FlightStatus.valueOf(r.status()), r.checkIn(),
                    r.belt(), Aircraft.of(r.aircraft()));
            // Put back the saved estimated time and cancel reason (also shifted)
            f.restore(new Flight.Snapshot(r.gate(), FlightStatus.valueOf(r.status()),
                    LocalDateTime.parse(r.estimated()).plus(shift), r.cancelReason()));
            return f;
        } catch (FidsException e) {
            // A saved flight broke a rule: report it as a file problem
            throw new IOException("Invalid flight record " + r.flightNumber() + ": " + e.getMessage(), e);
        }
    }
}
