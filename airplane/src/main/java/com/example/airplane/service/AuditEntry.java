package com.example.airplane.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One line of the change log.
 * A record is a short way to write an immutable data class (fields, constructor and getters are made for us).
 */
public record AuditEntry(LocalDateTime time, String actor, String description) {
    // Overrides Object.toString (polymorphism) so an entry prints nicely in the log list.
    @Override
    public String toString() {
        // Format: "14:05:09  [actor]  what happened"
        return time.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  [" + actor + "]  " + description;
    }
}
