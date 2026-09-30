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
 */
public class JsonDataSource implements FlightDataSource {
    record FlightRec(String flightNumber, String airline, String origin, String destination, String destinationCode,
                     String scheduled, String estimated, String gate, String status, String checkIn, String belt,
                     String aircraft, String cancelReason) { }

    record BookingRec(String reference, String passengerId, String passengerName, String flight, String seat,
                      String group, boolean checkedIn) { }

    record FileRec(String savedAt, List<FlightRec> flights, List<BookingRec> bookings) { }

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonDataSource(Path file) { this.file = file; }

    @Override
    public Snapshot load() throws IOException {
        if (!Files.exists(file)) {
            Snapshot seeded = SampleData.create(LocalDateTime.now());
            save(seeded);
            return seeded;
        }
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            FileRec rec = gson.fromJson(in, FileRec.class);
            if (rec == null || rec.flights() == null || rec.bookings() == null || rec.savedAt() == null) {
                throw new IOException("Data file is empty or incomplete: " + file);
            }
            Duration shift = Duration.between(LocalDateTime.parse(rec.savedAt()), LocalDateTime.now());
            List<Flight> flights = new ArrayList<>();
            for (FlightRec r : rec.flights()) flights.add(toFlight(r, shift));
            Map<String, Passenger> people = new HashMap<>();
            List<Booking> bookings = new ArrayList<>();
            for (BookingRec r : rec.bookings()) {
                Passenger p = people.computeIfAbsent(r.passengerId(), id -> new Passenger(id, r.passengerName()));
                bookings.add(new Booking(r.reference(), p, r.flight(), r.seat(), r.group(), r.checkedIn()));
            }
            return new Snapshot(flights, bookings);
        } catch (JsonParseException | IllegalArgumentException | java.time.DateTimeException e) {
            throw new IOException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void save(Snapshot s) throws IOException {
        List<FlightRec> flights = s.flights().stream().map(JsonDataSource::toRec).toList();
        List<BookingRec> bookings = s.bookings().stream().map(b -> new BookingRec(b.bookingReference(),
                b.passenger().id(), b.passenger().name(), b.linkedFlightNumber(), b.seat(), b.boardingGroup(),
                b.isCheckedIn())).toList();
        FileRec rec = new FileRec(LocalDateTime.now().toString(), flights, bookings);
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(rec, out);
        }
    }

    private static FlightRec toRec(Flight f) {
        return new FlightRec(f.flightNumber(), f.airline().code(), f.origin(), f.destination(), f.destinationCode(),
                f.scheduledTime().toString(), f.estimatedTime().toString(), f.gate(), f.status().name(),
                f.checkInCounter(), f.baggageBelt(), f.aircraft().key(), f.cancellationReason());
    }

    private static Flight toFlight(FlightRec r, Duration shift) throws IOException {
        try {
            LocalDateTime scheduled = LocalDateTime.parse(r.scheduled()).plus(shift);
            Flight f = new Flight(r.flightNumber(), SampleData.airline(r.airline()), r.origin(), r.destination(),
                    r.destinationCode(), scheduled, r.gate(), FlightStatus.valueOf(r.status()), r.checkIn(),
                    r.belt(), Aircraft.of(r.aircraft()));
            f.restore(new Flight.Snapshot(r.gate(), FlightStatus.valueOf(r.status()),
                    LocalDateTime.parse(r.estimated()).plus(shift), r.cancelReason()));
            return f;
        } catch (FidsException e) {
            throw new IOException("Invalid flight record " + r.flightNumber() + ": " + e.getMessage(), e);
        }
    }
}
