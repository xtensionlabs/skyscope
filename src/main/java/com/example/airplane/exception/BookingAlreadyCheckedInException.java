package com.example.airplane.exception;

/** Thrown when a passenger tries to check in twice for the same booking. (Custom exception, inheritance) */
public class BookingAlreadyCheckedInException extends FidsException {
    // Builds a friendly message that includes the booking reference
    public BookingAlreadyCheckedInException(String reference) {
        super("Booking " + reference + " is already checked in");
    }
}
