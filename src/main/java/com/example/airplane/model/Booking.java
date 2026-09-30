package com.example.airplane.model;

import com.example.airplane.exception.BookingAlreadyCheckedInException;

/** A passenger's seat on a flight. Tracks whether the passenger has checked in. */
public class Booking {
    private final String bookingReference;
    private final Passenger passenger;
    private final String linkedFlightNumber;
    private final String seat;
    private final String boardingGroup;
    private boolean checkedIn;

    public Booking(String bookingReference, Passenger passenger, String linkedFlightNumber,
                   String seat, String boardingGroup, boolean checkedIn) {
        this.bookingReference = bookingReference;
        this.passenger = passenger;
        this.linkedFlightNumber = linkedFlightNumber;
        this.seat = seat;
        this.boardingGroup = boardingGroup;
        this.checkedIn = checkedIn;
    }

    public String bookingReference() { return bookingReference; }
    public Passenger passenger() { return passenger; }
    public String passengerName() { return passenger.name(); }
    public String linkedFlightNumber() { return linkedFlightNumber; }
    public String seat() { return seat; }
    public String boardingGroup() { return boardingGroup; }
    public boolean isCheckedIn() { return checkedIn; }

    /** Marks the booking as checked in; a booking can only be checked in once. */
    public void checkIn() throws BookingAlreadyCheckedInException {
        if (checkedIn) throw new BookingAlreadyCheckedInException(bookingReference);
        checkedIn = true;
    }
}
