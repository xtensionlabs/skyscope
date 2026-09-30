package com.example.airplane.exception;

/** Thrown when a gate id is malformed or does not exist (valid gates are A1-A8, B1-B8, C1-C8). */
public class InvalidGateException extends FidsException {
    public InvalidGateException(String gate) {
        super("\"" + gate + "\" is not a valid gate (use A1-A8, B1-B8 or C1-C8)");
    }
}
