package com.example.airplane.model;

/** Twin-aisle jet for long-haul routes. */
public class WideBody extends Aircraft {
    public WideBody(String key, String name, int seats) { super(key, name, seats); }
    @Override public String category() { return "Wide-body"; }
    @Override public int cabinCrew() { return Math.max(8, seats() / 30); }
}
