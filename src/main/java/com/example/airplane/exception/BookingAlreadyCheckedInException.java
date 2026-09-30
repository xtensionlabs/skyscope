package com.example.airplane.exception;

/** Thrown when a passenger tries to check in twice for the same booking. */
public class BookingAlreadyCheckedInException extends FidsException {
    public BookingAlreadyCheckedInException(String reference) {
        super("Booking " + reference + " is already checked in");
    }
}
