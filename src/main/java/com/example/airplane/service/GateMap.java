package com.example.airplane.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal geometry (1000 x 640 map space) and route computation.
 * Pier A runs left, Pier B runs up, Pier C runs right from the central plaza.
 */
public final class GateMap {
    public record Pt(double x, double y) { }
    public record GateSlot(String gate, Pt door, Pt box) { }

    public static final Pt YOU_ARE_HERE = new Pt(500, 575);
    public static final Pt SECURITY = new Pt(500, 470);
    public static final Pt PLAZA = new Pt(500, 390);
    public static final double CORRIDOR_Y = 390;
    public static final double CORRIDOR_X = 500;
    private static final double METRES_PER_UNIT = 0.35;

    /** Gate slot for ids like "A3" (pier letter + number 1-8), or null if unknown. */
    public static GateSlot slot(String gate) {
        if (gate == null || gate.length() < 2) return null;
        char pier = gate.charAt(0);
        int n;
        try { n = Integer.parseInt(gate.substring(1)); } catch (NumberFormatException e) { return null; }
        if (n < 1 || n > 8) return null;
        int idx = (n - 1) / 2;            // 0..3 distance along the pier
        boolean far = n % 2 == 0;         // even gates on the far side of the corridor
        double off = far ? 48 : -48;
        switch (pier) {
            case 'A': {
                double x = 400 - idx * 80;
                return new GateSlot(gate, new Pt(x, CORRIDOR_Y), new Pt(x, CORRIDOR_Y + off));
            }
            case 'C': {
                double x = 600 + idx * 80;
                return new GateSlot(gate, new Pt(x, CORRIDOR_Y), new Pt(x, CORRIDOR_Y + off));
            }
            case 'B': {
                double y = 280 - idx * 70;
                return new GateSlot(gate, new Pt(CORRIDOR_X, y), new Pt(CORRIDOR_X + off * 1.15, y));
            }
            default:
                return null;
        }
    }

    /** Waypoints from the "You are here" marker to the gate's box. */
    public static List<Pt> route(String gate) {
        GateSlot s = slot(gate);
        List<Pt> pts = new ArrayList<>(List.of(YOU_ARE_HERE, SECURITY, PLAZA));
        if (s == null) return pts;
        if (s.gate().charAt(0) == 'B') pts.add(new Pt(CORRIDOR_X, s.door().y()));
        else pts.add(new Pt(s.door().x(), CORRIDOR_Y));
        pts.add(s.box());
        return pts;
    }

    public static double lengthMetres(List<Pt> pts) {
        double len = 0;
        for (int i = 1; i < pts.size(); i++) {
            len += Math.hypot(pts.get(i).x() - pts.get(i - 1).x(), pts.get(i).y() - pts.get(i - 1).y());
        }
        return len * METRES_PER_UNIT;
    }

    private GateMap() { }
}
