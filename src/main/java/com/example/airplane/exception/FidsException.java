package com.example.airplane.exception;

/** Base class of all checked business-rule errors in the FIDS application. */
public class FidsException extends Exception {
    public FidsException(String message) {
        super(message);
    }
}
