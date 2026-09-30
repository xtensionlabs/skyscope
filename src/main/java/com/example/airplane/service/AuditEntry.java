package com.example.airplane.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** One line of the change log. */
public record AuditEntry(LocalDateTime time, String actor, String description) {
    @Override
    public String toString() {
        return time.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  [" + actor + "]  " + description;
    }
}
