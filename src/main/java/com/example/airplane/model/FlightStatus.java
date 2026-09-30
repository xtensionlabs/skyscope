package com.example.airplane.model;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle states of a flight. Declaration order doubles as the sort rank. Each constant supplies its own
 * legal transitions and announcement text (polymorphism via {@link StatusBehaviour}).
 */
public enum FlightStatus implements StatusBehaviour {
    ON_TIME("On Time", "ok", "mdi2c-check-circle") {
        @Override public Set<FlightStatus> nextStatuses() { return EnumSet.of(BOARDING, DELAYED, CANCELLED); }
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
        @Override public Set<FlightStatus> nextStatuses() { return EnumSet.noneOf(FlightStatus.class); }
        @Override public String announcement(Flight f) {
            return f.displayNumber() + " to " + f.destination() + " has departed.";
        }
    },
    CANCELLED("Cancelled", "bad", "mdi2c-close-circle") {
        @Override public Set<FlightStatus> nextStatuses() { return EnumSet.noneOf(FlightStatus.class); }
        @Override public String announcement(Flight f) {
            return f.displayNumber() + " to " + f.destination() + " has been cancelled. " + f.cancellationReason();
        }
    };

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final String label;
    private final String styleKey;
    private final String icon;

    FlightStatus(String label, String styleKey, String icon) {
        this.label = label;
        this.styleKey = styleKey;
        this.icon = icon;
    }

    private static String time(Flight f) { return f.estimatedTime().format(HHMM); }

    public String label() { return label; }
    /** CSS suffix: ok / warn / bad / muted. */
    public String styleKey() { return styleKey; }
    public String icon() { return icon; }
    public boolean isActive() { return this != DEPARTED && this != CANCELLED; }
}
