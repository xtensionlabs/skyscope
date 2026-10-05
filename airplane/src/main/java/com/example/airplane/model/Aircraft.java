package com.example.airplane.model;

import java.util.List;

/**
 * Abstract base of the aircraft hierarchy (NarrowBody, WideBody, RegionalJet).
 * OOP: abstraction - an abstract class cannot be created with new; it only defines what all aircraft share.
 */
public abstract class Aircraft {
    // Encapsulation: private final fields, set once in the constructor
    private final String key;    // short code e.g. "A320"
    private final String name;   // full name e.g. "Airbus A320"
    private final int seats;     // number of passenger seats

    // protected: only this class's subclasses can call it (through super(...))
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
    // Abstract method: no body here, every subclass must write its own (polymorphism)
    public abstract String category();

    /** Minimum cabin crew required (depends on the category). */
    public abstract int cabinCrew();

    // One-line summary built from the name, category (subclass-specific) and seats
    public String describe() { return name + " · " + category() + " · " + seats + " seats"; }

    // When an Aircraft is printed or shown in a list, display its key
    @Override
    public String toString() { return key; }

    // Immutable list of all aircraft codes the system knows about
    public static List<String> knownKeys() {
        return List.of("A320", "B737-800", "E190", "DHC-8", "A330-300", "B787-8", "B777-300ER");
    }

    /** Factory: builds the right subclass from a key (unknown keys become a generic narrow-body). */
    public static Aircraft of(String key) {
        // Switch expression: picks the case matching the key and returns the new object
        return switch (key) {
            case "A320" -> new NarrowBody(key, "Airbus A320", 150);
            case "B737-800" -> new NarrowBody(key, "Boeing 737-800", 162);
            case "E190" -> new RegionalJet(key, "Embraer E190", 100);
            case "DHC-8" -> new RegionalJet(key, "De Havilland Dash 8", 78);
            case "A330-300" -> new WideBody(key, "Airbus A330-300", 277);
            case "B787-8" -> new WideBody(key, "Boeing 787-8", 234);
            case "B777-300ER" -> new WideBody(key, "Boeing 777-300ER", 354);
            // Fallback for any other key
            default -> new NarrowBody(key, key, 150);
        };
    }
}
