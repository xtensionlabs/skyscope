package com.example.airplane.model;

import java.util.List;

/** Abstract base of the aircraft hierarchy (NarrowBody, WideBody, RegionalJet). */
public abstract class Aircraft {
    private final String key;
    private final String name;
    private final int seats;

    protected Aircraft(String key, String name, int seats) {
        this.key = key;
        this.name = name;
        this.seats = seats;
    }

    /** Short persistent id, e.g. "B787-8". */
    public String key() { return key; }
    public String name() { return name; }
    public int seats() { return seats; }

    /** Size category shown to users. */
    public abstract String category();

    /** Minimum cabin crew required (depends on the category). */
    public abstract int cabinCrew();

    public String describe() { return name + " · " + category() + " · " + seats + " seats"; }

    @Override
    public String toString() { return key; }

    public static List<String> knownKeys() {
        return List.of("A320", "B737-800", "E190", "DHC-8", "A330-300", "B787-8", "B777-300ER");
    }

    /** Factory: builds the right subclass from a key (unknown keys become a generic narrow-body). */
    public static Aircraft of(String key) {
        return switch (key) {
            case "A320" -> new NarrowBody(key, "Airbus A320", 150);
            case "B737-800" -> new NarrowBody(key, "Boeing 737-800", 162);
            case "E190" -> new RegionalJet(key, "Embraer E190", 100);
            case "DHC-8" -> new RegionalJet(key, "De Havilland Dash 8", 78);
            case "A330-300" -> new WideBody(key, "Airbus A330-300", 277);
            case "B787-8" -> new WideBody(key, "Boeing 787-8", 234);
            case "B777-300ER" -> new WideBody(key, "Boeing 777-300ER", 354);
            default -> new NarrowBody(key, key, 150);
        };
    }
}
