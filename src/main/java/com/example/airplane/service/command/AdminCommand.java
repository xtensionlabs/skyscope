package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.service.FlightService;

/**
 * A staff action that can be executed, described in the audit log, and undone (Command pattern).
 * Abstract class: it only declares what every command must do; subclasses fill in the details (abstraction).
 */
public abstract class AdminCommand {
    // Carry out the action on the given service.
    public abstract void execute(FlightService service) throws FidsException;

    // Reverse the action (this is what makes Undo possible).
    public abstract void undo(FlightService service) throws FidsException;

    /** Human-readable summary; valid after {@link #execute}. */
    public abstract String describe();
}
