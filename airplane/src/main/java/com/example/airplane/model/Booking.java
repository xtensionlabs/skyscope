package com.example.airplane.model;

import com.example.airplane.exception.BookingAlreadyCheckedInException;

/** A passenger's seat on a flight. Tracks whether the passenger has checked in. */
public class Booking {
    // Encapsulation: all fields are private and final (cannot change) except checkedIn
    private final String bookingReference;   // unique booking code e.g. "BK7F3A"
    private final Passenger passenger;       // who owns the booking (association with Passenger)
    private final String linkedFlightNumber; // flight number this booking is for
    private final String seat;               // seat e.g. "14A"
    private final String boardingGroup;      // boarding group e.g. "Group 3"
    private boolean checkedIn;               // the only value that changes after creation

    // Constructor: copies every argument into the matching field
    public Booking(String bookingReference, Passenger passenger, String linkedFlightNumber,
                   String seat, String boardingGroup, boolean checkedIn) {
        this.bookingReference = bookingReference;
        this.passenger = passenger;
        this.linkedFlightNumber = linkedFlightNumber;
        this.seat = seat;
        this.boardingGroup = boardingGroup;
        this.checkedIn = checkedIn;
    }

    // Getters (encapsulation): read-only access to the private fields
    public String bookingReference() { return bookingReference; }
    public Passenger passenger() { return passenger; }
    public String passengerName() { return passenger.name(); }   // shortcut: the passenger's name
    public String linkedFlightNumber() { return linkedFlightNumber; }
    public String seat() { return seat; }
    public String boardingGroup() { return boardingGroup; }
    public boolean isCheckedIn() { return checkedIn; }

    /** Marks the booking as checked in; a booking can only be checked in once. */
    public void checkIn() throws BookingAlreadyCheckedInException {
        // Exception handling: refuse (throw) if already checked in
        if (checkedIn) throw new BookingAlreadyCheckedInException(bookingReference);
        checkedIn = true;
    }
}
