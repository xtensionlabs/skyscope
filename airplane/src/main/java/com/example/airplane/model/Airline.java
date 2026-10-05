package com.example.airplane.model;

/**
 * Airline identity: IATA code, display name and brand colour for the simple logo chip.
 * OOP: a record - a short way to write an immutable data class (fields, constructor and getters like code() are auto-made).
 */
public record Airline(String code, String name, String color) { }
