package com.example.airplane.model;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle states of a flight. Declaration order doubles as the sort rank. Each constant supplies its own
 * legal transitions and announcement text (polymorphism via {@link StatusBehaviour}).
 * OOP: enum - a fixed set of named values; this one implements an interface and has fields and methods.
 */
public enum FlightStatus implements StatusBehaviour {
    // Each constant: (label shown, CSS style key, icon code) plus its own method bodies
    ON_TIME("On Time", "ok", "mdi2c-check-circle") {
        // From On Time a flight can start boarding, be delayed or be cancelled
        @Override public Set<FlightStatus> nextStatuses() { return EnumSet.of(BOARDING, DELAYED, CANCELLED); }
        // Text spoken/shown for this status
        @Override public String announcement(Flight f) {
            return f.displayNumber() + " to " + f.destination() + " is on time, departing at " + time(f) + ".";
        }
    },
    BOARDING("Boarding", "warn", "mdi2a-airplane-takeoff") {
        @Override public Set<FlightStatus> nextStatuses() { return EnumSet.of(GATE_CLOSED, CANCELLED); }
        @Override public String announcement(Flight f) {
            return f.displayNumber() + " to " + f.destination() + " is now boarding at gate " + f.gate() + ".";
        }
    },
    DELAYED("Delayed", "bad", "mdi2c-clock-outline") {
        @Override public Set<FlightStatus> nextStatuses() { return EnumSet.of(BOARDING, CANCELLED); }
        @Override public String announcement(Flight f) {
            return f.displayNumber() + " to " + f.destination() + " is delayed. New departure time " + time(f) + ".";
        }
    },
    GATE_CLOSED("Gate Closed", "warn", "mdi2d-door-closed") {
        @Override public Set<FlightStatus> nextStatuses() { return EnumSet.of(DEPARTED, CANCELLED); }
        @Override public String announcement(Flight f) {
            return "Gate " + f.gate() + " is now closed for " + f.displayNumber() + " to " + f.destination() + ".";
        }
    },
    DEPARTED("Departed", "muted", "mdi2a-airplane") {
        // Final state: an empty set means no further changes allowed
        @Override public Set<FlightStatus> nextStatuses() { return EnumSet.noneOf(FlightStatus.class); }
        @Override public String announcement(Flight f) {
            return f.displayNumber() + " to " + f.destination() + " has departed.";
        }
    },
    CANCELLED("Cancelled", "bad", "mdi2c-close-circle") {
        // Final state too
        @Override public Set<FlightStatus> nextStatuses() { return EnumSet.noneOf(FlightStatus.class); }
        @Override public String announcement(Flight f) {
            return f.displayNumber() + " to " + f.destination() + " has been cancelled. " + f.cancellationReason();
        }
    };

    // Formatter that prints a time like "14:05"
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // Encapsulation: each constant stores its own private data
    private final String label;     // text shown to users
    private final String styleKey;  // used to pick a colour in the UI
    private final String icon;      // icon name for the UI

    // Enum constructor (runs once per constant above)
    FlightStatus(String label, String styleKey, String icon) {
        this.label = label;
        this.styleKey = styleKey;
        this.icon = icon;
    }

    // Helper: the flight's estimated departure time as "HH:mm"
    private static String time(Flight f) { return f.estimatedTime().format(HHMM); }

    public String label() { return label; }
    /** CSS suffix: ok / warn / bad / muted. */
    public String styleKey() { return styleKey; }
    public String icon() { return icon; }
    // Active = not yet finished (anything except Departed or Cancelled)
    public boolean isActive() { return this != DEPARTED && this != CANCELLED; }
}
