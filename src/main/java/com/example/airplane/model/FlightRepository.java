package com.example.airplane.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** In-memory sample data. Departure times are relative to launch so the board always looks live. */
public class FlightRepository {
    private static final Map<String, Airline> AIRLINES = new LinkedHashMap<>();
    static {
        for (Airline a : List.of(
                new Airline("KQ", "Kenya Airways", "#c8102e"), new Airline("ET", "Ethiopian Airlines", "#0b8a3e"),
                new Airline("EK", "Emirates", "#d71921"), new Airline("QR", "Qatar Airways", "#7a1f4b"),
                new Airline("BA", "British Airways", "#1c3f94"), new Airline("KL", "KLM", "#00a1de"),
                new Airline("TK", "Turkish Airlines", "#e81932"), new Airline("LH", "Lufthansa", "#05164d"),
                new Airline("UR", "Uganda Airlines", "#b58900"), new Airline("WB", "RwandAir", "#0a5da8"),
                new Airline("AF", "Air France", "#002157"), new Airline("JM", "Jambojet", "#e4572e"))) {
            AIRLINES.put(a.code(), a);
        }
    }

    private final List<Flight> flights = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();

    public FlightRepository(LocalDateTime now) {
        LocalDateTime base = now.truncatedTo(ChronoUnit.MINUTES);
        // number, destination, code, minutes from now, gate, status, check-in, baggage belt
        add(base, "KQ100", "London Heathrow", "LHR", -6, "B2", FlightStatus.DEPARTED, "D 41-46", 3);
        add(base, "ET301", "Addis Ababa", "ADD", 4, "A3", FlightStatus.GATE_CLOSED, "B 12-16", 1);
        add(base, "EK720", "Dubai", "DXB", 12, "C5", FlightStatus.BOARDING, "E 51-58", 4);
        add(base, "JM602", "Mombasa", "MBA", 18, "A1", FlightStatus.BOARDING, "A 01-05", 2);
        add(base, "QR1402", "Doha", "DOH", 25, "C2", FlightStatus.ON_TIME, "E 60-66", 4);
        add(base, "KQ412", "Kigali", "KGL", 30, "A6", FlightStatus.ON_TIME, "B 20-24", 1);
        add(base, "TK606", "Istanbul", "IST", 38, "C7", FlightStatus.DELAYED, "F 71-77", 5);
        add(base, "BA064", "London Heathrow", "LHR", 45, "B4", FlightStatus.ON_TIME, "D 30-38", 3);
        add(base, "WB461", "Kigali", "KGL", 52, "A2", FlightStatus.ON_TIME, "B 25-28", 1);
        add(base, "KL564", "Amsterdam", "AMS", 60, "B6", FlightStatus.ON_TIME, "D 47-50", 3);
        add(base, "LH591", "Frankfurt", "FRA", 70, "B1", FlightStatus.ON_TIME, "D 51-56", 3);
        add(base, "UR341", "Entebbe", "EBB", 75, "A5", FlightStatus.ON_TIME, "B 30-34", 2);
        add(base, "KQ530", "Johannesburg", "JNB", 85, "C3", FlightStatus.ON_TIME, "E 40-46", 4);
        add(base, "AF941", "Paris CDG", "CDG", 95, "B8", FlightStatus.DELAYED, "D 57-62", 3);
        add(base, "ET309", "Addis Ababa", "ADD", 105, "A4", FlightStatus.ON_TIME, "B 35-38", 1);
        add(base, "JM610", "Zanzibar", "ZNZ", 115, "A7", FlightStatus.ON_TIME, "A 06-10", 2);
        add(base, "EK722", "Dubai", "DXB", 125, "C6", FlightStatus.ON_TIME, "E 67-72", 4);
        add(base, "KQ202", "Lagos", "LOS", 140, "C1", FlightStatus.CANCELLED, "F 80-84", 5);
        add(base, "QR1404", "Doha", "DOH", 155, "C4", FlightStatus.ON_TIME, "E 73-78", 4);
        add(base, "TK608", "Istanbul", "IST", 170, "C8", FlightStatus.ON_TIME, "F 85-90", 5);
        add(base, "KQ004", "New York JFK", "JFK", 185, "B3", FlightStatus.ON_TIME, "D 10-20", 3);
        add(base, "KQ610", "Dar es Salaam", "DAR", 200, "A8", FlightStatus.ON_TIME, "B 40-43", 2);

        bookings.add(new Booking("BK7F3A", "Amina Wanjiru", "BA064", "14A", "Group 3"));
        bookings.add(new Booking("QX92LM", "Daniel Otieno", "EK720", "22C", "Group 5"));
        bookings.add(new Booking("PL48ZT", "Grace Mutua", "KQ004", "3F", "Group 1"));
        bookings.add(new Booking("RT55HD", "Samuel Kiptoo", "KL564", "31B", "Group 4"));
        bookings.add(new Booking("MN20CW", "Lina Hassan", "KQ412", "9D", "Group 2"));

        for (Flight f : flights) {
            if (f.status() == FlightStatus.DELAYED) f.setEstimatedTime(f.scheduledTime().plusMinutes(35));
        }
    }

    private void add(LocalDateTime base, String no, String dest, String code, int mins, String gate,
                     FlightStatus st, String checkIn, int belt) {
        Airline al = AIRLINES.get(no.substring(0, 2));
        String terminal = "Terminal " + (gate.charAt(0) - 'A' + 1);
        flights.add(new Flight(no, al, "Nairobi", dest, code, base.plusMinutes(mins), gate, terminal, st,
                checkIn, "Belt " + belt));
    }

    public List<Flight> flights() { return flights; }

    public Optional<Flight> byId(String id) {
        return id == null ? Optional.empty() : flights.stream().filter(f -> f.id().equals(id)).findFirst();
    }

    public Optional<Booking> bookingFor(String flightNumber) {
        return bookings.stream().filter(b -> b.linkedFlightNumber().equals(flightNumber)).findFirst();
    }

    /** Finds a flight by flight number or booking reference (case/space-insensitive). */
    public Optional<Flight> lookup(String query) {
        String q = query == null ? "" : query.replaceAll("\\s+", "").toUpperCase();
        if (q.isEmpty()) return Optional.empty();
        Optional<Flight> direct = byId(q);
        if (direct.isPresent()) return direct;
        return bookings.stream().filter(b -> b.bookingReference().equals(q)).findFirst()
                .flatMap(b -> byId(b.linkedFlightNumber()));
    }
}
