package com.example.airplane.model;

/** Small aircraft for short domestic and regional hops. */
public class RegionalJet extends Aircraft {
    public RegionalJet(String key, String name, int seats) { super(key, name, seats); }
    @Override public String category() { return "Regional"; }
    @Override public int cabinCrew() { return 2; }
}
