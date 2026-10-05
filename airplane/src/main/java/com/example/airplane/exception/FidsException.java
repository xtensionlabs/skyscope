package com.example.airplane.exception;

/**
 * Base class of all checked business-rule errors in the FIDS application.
 * OOP: inheritance - it extends Exception, and all our other exceptions extend this class,
 * so one catch block for FidsException can handle every custom error.
 */
public class FidsException extends Exception {
    // Constructor: passes the error message up to Exception so getMessage() returns it
    public FidsException(String message) {
        super(message);
    }
}
