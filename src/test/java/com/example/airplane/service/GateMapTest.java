package com.example.airplane.service;

import com.example.airplane.service.GateMap.Pt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GateMapTest {
    @Test
    void everyValidGateHasASlot() {
        for (String pier : new String[]{"A", "B", "C"}) {
            for (int n = 1; n <= 8; n++) assertNotNull(GateMap.slot(pier + n), pier + n);
        }
    }

    @Test
    void invalidGatesHaveNoSlot() {
        assertNull(GateMap.slot(null));
        assertNull(GateMap.slot("Z1"));
        assertNull(GateMap.slot("A9"));
        assertNull(GateMap.slot("A"));
        assertNull(GateMap.slot("Ax"));
    }

    @Test
    void routeStartsAtYouAreHereAndEndsAtGate() {
        List<Pt> route = GateMap.route("B4");
        assertEquals(GateMap.YOU_ARE_HERE, route.get(0));
        assertEquals(GateMap.slot("B4").box(), route.get(route.size() - 1));
        assertEquals(GateMap.PLAZA, route.get(2));
    }

    @Test
    void routeToUnknownGateStopsAtPlaza() {
        assertEquals(3, GateMap.route("Q9").size());
    }

    @Test
    void routeUsesCorridorBeforeTurningIntoGate() {
        // Pier A runs horizontally, so the corner must be on the corridor line at the gate's x.
        List<Pt> route = GateMap.route("A3");
        Pt corner = route.get(route.size() - 2);
        assertEquals(GateMap.CORRIDOR_Y, corner.y());
        assertEquals(GateMap.slot("A3").door().x(), corner.x());
    }

    @Test
    void furtherGatesAreLongerWalks() {
        assertTrue(GateMap.lengthMetres(GateMap.route("A8")) > GateMap.lengthMetres(GateMap.route("A1")));
        assertTrue(GateMap.lengthMetres(GateMap.route("B8")) > GateMap.lengthMetres(GateMap.route("B1")));
        assertTrue(GateMap.lengthMetres(GateMap.route("C1")) > 0);
    }
}
