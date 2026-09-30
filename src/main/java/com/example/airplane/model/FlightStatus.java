package com.example.airplane.model;

/** Lifecycle states of a flight. Declaration order doubles as the sort rank. */
public enum FlightStatus {
    ON_TIME("On Time", "ok", "mdi2c-check-circle"),
    BOARDING("Boarding", "warn", "mdi2a-airplane-takeoff"),
    DELAYED("Delayed", "bad", "mdi2c-clock-outline"),
    GATE_CLOSED("Gate Closed", "warn", "mdi2d-door-closed"),
    DEPARTED("Departed", "muted", "mdi2a-airplane"),
    CANCELLED("Cancelled", "bad", "mdi2c-close-circle");

    private final String label;
    private final String styleKey;
    private final String icon;

    FlightStatus(String label, String styleKey, String icon) {
        this.label = label;
        this.styleKey = styleKey;
        this.icon = icon;
    }

    public String label() { return label; }
    /** CSS suffix: ok / warn / bad / muted. */
    public String styleKey() { return styleKey; }
    public String icon() { return icon; }
    public boolean isActive() { return this != DEPARTED && this != CANCELLED; }
}
