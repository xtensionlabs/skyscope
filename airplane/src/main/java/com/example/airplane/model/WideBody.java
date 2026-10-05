package com.example.airplane.model;

/** Twin-aisle jet for long-haul routes. (Inheritance: a WideBody IS-A Aircraft) */
public class WideBody extends Aircraft {
    // Constructor just passes the details to the parent Aircraft constructor
    public WideBody(String key, String name, int seats) { super(key, name, seats); }
    // Polymorphism: overrides the abstract method with this class's own answer
    @Override public String category() { return "Wide-body"; }
    // At least 8 crew, or 1 per 30 seats if that is larger
    @Override public int cabinCrew() { return Math.max(8, seats() / 30); }
}
