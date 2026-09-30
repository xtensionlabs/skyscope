package com.example.airplane.model;

/** Single-aisle jet for short and medium routes. */
public class NarrowBody extends Aircraft {
    public NarrowBody(String key, String name, int seats) { super(key, name, seats); }
    @Override public String category() { return "Narrow-body"; }
    @Override public int cabinCrew() { return Math.max(3, seats() / 50); }
}
