package com.example.airplane.model;

/** A passenger booking linked to a flight by flight number. */
public record Booking(String bookingReference, String passengerName, String linkedFlightNumber,
                      String seat, String boardingGroup) { }
