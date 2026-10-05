package com.example.airplane.model;

/** Single-aisle jet for short and medium routes. (Inheritance: a NarrowBody IS-A Aircraft) */
public class NarrowBody extends Aircraft {
    // Constructor just passes the details to the parent Aircraft constructor
    public NarrowBody(String key, String name, int seats) { super(key, name, seats); }
    // Polymorphism: our own version of the abstract method from Aircraft
    @Override public String category() { return "Narrow-body"; }
    // At least 3 crew, or 1 per 50 seats if that is larger
    @Override public int cabinCrew() { return Math.max(3, seats() / 50); }
}
