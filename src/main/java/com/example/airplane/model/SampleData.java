package com.example.airplane.model;

import com.example.airplane.exception.FidsException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Hard-coded demo data. Departure times are relative to "now" so the board always looks live. */
public final class SampleData {
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

    public static List<Airline> airlines() { return List.copyOf(AIRLINES.values()); }

    /** Looks up an airline by IATA code; unknown codes get a neutral grey placeholder. */
    public static Airline airline(String code) {
        return AIRLINES.getOrDefault(code, new Airline(code, code, "#555555"));
    }

    public static FlightDataSource.Snapshot create(LocalDateTime now) {
        LocalDateTime base = now.truncatedTo(ChronoUnit.MINUTES);
        List<Flight> f = new ArrayList<>();
        // number, destination, code, minutes from now, gate, status, check-in, belt, aircraft
        add(f, base, "KQ100", "London Heathrow", "LHR", -6, "B2", FlightStatus.DEPARTED, "D 41-46", 3, "B787-8");
        add(f, base, "ET301", "Addis Ababa", "ADD", 4, "A3", FlightStatus.GATE_CLOSED, "B 12-16", 1, "B737-800");
        add(f, base, "EK720", "Dubai", "DXB", 12, "C5", FlightStatus.BOARDING, "E 51-58", 4, "B777-300ER");
        add(f, base, "JM602", "Mombasa", "MBA", 18, "A1", FlightStatus.BOARDING, "A 01-05", 2, "DHC-8");
        add(f, base, "QR1402", "Doha", "DOH", 25, "C2", FlightStatus.ON_TIME, "E 60-66", 4, "A330-300");
        add(f, base, "KQ412", "Kigali", "KGL", 30, "A6", FlightStatus.ON_TIME, "B 20-24", 1, "E190");
        add(f, base, "TK606", "Istanbul", "IST", 38, "C7", FlightStatus.DELAYED, "F 71-77", 5, "A320");
        add(f, base, "BA064", "London Heathrow", "LHR", 45, "B4", FlightStatus.ON_TIME, "D 30-38", 3, "B787-8");
        add(f, base, "WB461", "Kigali", "KGL", 52, "A2", FlightStatus.ON_TIME, "B 25-28", 1, "B737-800");
        add(f, base, "KL564", "Amsterdam", "AMS", 60, "B6", FlightStatus.ON_TIME, "D 47-50", 3, "B787-8");
        add(f, base, "LH591", "Frankfurt", "FRA", 70, "B1", FlightStatus.ON_TIME, "D 51-56", 3, "A330-300");
        add(f, base, "UR341", "Entebbe", "EBB", 75, "A5", FlightStatus.ON_TIME, "B 30-34", 2, "A320");
        add(f, base, "KQ530", "Johannesburg", "JNB", 85, "C3", FlightStatus.ON_TIME, "E 40-46", 4, "B787-8");
        add(f, base, "AF941", "Paris CDG", "CDG", 95, "B8", FlightStatus.DELAYED, "D 57-62", 3, "B777-300ER");
        add(f, base, "ET309", "Addis Ababa", "ADD", 105, "A4", FlightStatus.ON_TIME, "B 35-38", 1, "B737-800");
        add(f, base, "JM610", "Zanzibar", "ZNZ", 115, "A7", FlightStatus.ON_TIME, "A 06-10", 2, "DHC-8");
        add(f, base, "EK722", "Dubai", "DXB", 125, "C6", FlightStatus.ON_TIME, "E 67-72", 4, "B777-300ER");
        add(f, base, "KQ202", "Lagos", "LOS", 140, "C1", FlightStatus.CANCELLED, "F 80-84", 5, "B737-800");
        add(f, base, "QR1404", "Doha", "DOH", 155, "C4", FlightStatus.ON_TIME, "E 73-78", 4, "A330-300");
        add(f, base, "TK608", "Istanbul", "IST", 170, "C8", FlightStatus.ON_TIME, "F 85-90", 5, "B777-300ER");
        add(f, base, "KQ004", "New York JFK", "JFK", 185, "B3", FlightStatus.ON_TIME, "D 10-20", 3, "B787-8");
        add(f, base, "KQ610", "Dar es Salaam", "DAR", 200, "A8", FlightStatus.ON_TIME, "B 40-43", 2, "E190");

        // Delayed / cancelled flights carry the matching extra data.
        for (Flight fl : f) {
            if (fl.status() == FlightStatus.DELAYED) {
                fl.restore(new Flight.Snapshot(fl.gate(), FlightStatus.DELAYED,
                        fl.scheduledTime().plusMinutes(35), ""));
            } else if (fl.status() == FlightStatus.CANCELLED) {
                fl.restore(new Flight.Snapshot(fl.gate(), FlightStatus.CANCELLED, fl.scheduledTime(),
                        "Aircraft unavailable."));
            }
        }

        Passenger amina = new Passenger("P1", "Amina Wanjiru");
        Passenger daniel = new Passenger("P2", "Daniel Otieno");
        Passenger grace = new Passenger("P3", "Grace Mutua");
        Passenger samuel = new Passenger("P4", "Samuel Kiptoo");
        Passenger lina = new Passenger("P5", "Lina Hassan");
        List<Booking> b = new ArrayList<>(List.of(
                new Booking("BK7F3A", amina, "BA064", "14A", "Group 3", false),
                new Booking("BK2M9Q", amina, "KQ610", "7C", "Group 2", false),
                new Booking("QX92LM", daniel, "EK720", "22C", "Group 5", false),
                new Booking("PL48ZT", grace, "KQ004", "3F", "Group 1", false),
                new Booking("RT55HD", samuel, "KL564", "31B", "Group 4", false),
                new Booking("MN20CW", lina, "KQ412", "9D", "Group 2", false)));
        return new FlightDataSource.Snapshot(f, b);
    }

    private static void add(List<Flight> out, LocalDateTime base, String no, String dest, String code, int mins,
                            String gate, FlightStatus st, String checkIn, int belt, String aircraft) {
        try {
            out.add(new Flight(no, airline(no.substring(0, 2)), "Nairobi", dest, code, base.plusMinutes(mins), gate,
                    st, checkIn, "Belt " + belt, Aircraft.of(aircraft)));
        } catch (FidsException e) {
            throw new IllegalStateException("Bad sample data for " + no + ": " + e.getMessage(), e);
        }
    }

    private SampleData() { }
}
