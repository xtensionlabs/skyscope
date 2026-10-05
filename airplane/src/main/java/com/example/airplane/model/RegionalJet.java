package com.example.airplane.model;

/** Small aircraft for short domestic and regional hops. (Inheritance: a RegionalJet IS-A Aircraft) */
public class RegionalJet extends Aircraft {
    // Constructor just passes the details to the parent Aircraft constructor
    public RegionalJet(String key, String name, int seats) { super(key, name, seats); }
    // Polymorphism: overrides the abstract method with this class's own answer
    @Override public String category() { return "Regional"; }
    // Small planes always use a fixed crew of 2
    @Override public int cabinCrew() { return 2; }
}
