package com.example.airplane.service.command;

import com.example.airplane.exception.FidsException;
import com.example.airplane.service.FlightService;

/** A staff action that can be executed, described in the audit log, and undone (Command pattern). */
public abstract class AdminCommand {
    public abstract void execute(FlightService service) throws FidsException;

    public abstract void undo(FlightService service) throws FidsException;

    /** Human-readable summary; valid after {@link #execute}. */
    public abstract String describe();
}
