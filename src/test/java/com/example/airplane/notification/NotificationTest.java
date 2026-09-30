package com.example.airplane.notification;

import com.example.airplane.TestData;
import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;
import com.example.airplane.service.FlightEvent;
import com.example.airplane.service.FlightEvent.Type;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {
    @Test
    void gateChangeIsBroadcastAndMentionsBothGates() throws Exception {
        Flight f = TestData.flight("KQ412");
        String old = f.changeGate("A4");
        Notification n = Notification.from(new FlightEvent(Type.GATE_CHANGED, f, old)).orElseThrow();
        assertInstanceOf(GateChangeNotification.class, n);
        assertTrue(n.isBroadcast());
        assertTrue(n.body().contains("A4") && n.body().contains("A6"));
    }

    @Test
    void boardingCallIsNotBroadcast() {
        Flight f = TestData.flight("EK720");
        Notification n = Notification.from(new FlightEvent(Type.STATUS_CHANGED, f, "On Time")).orElseThrow();
        assertInstanceOf(BoardingCallNotification.class, n);
        assertFalse(n.isBroadcast());
    }

    @Test
    void otherStatusChangesAreQuiet() {
        Flight f = TestData.flight("BA064");
        assertEquals(Optional.empty(), Notification.from(new FlightEvent(Type.STATUS_CHANGED, f, "x")));
        assertEquals(Optional.empty(), Notification.from(new FlightEvent(Type.UPDATED, f, null)));
        assertEquals(Optional.empty(), Notification.from(new FlightEvent(Type.ADDED, f, null)));
    }

    @Test
    void cancellationIsBroadcast() throws Exception {
        Flight f = TestData.flight("BA064");
        f.cancel("Storm");
        Notification n = Notification.from(new FlightEvent(Type.CANCELLED, f, "On Time")).orElseThrow();
        assertInstanceOf(CancellationNotification.class, n);
        assertTrue(n.isBroadcast());
        assertEquals(FlightStatus.CANCELLED, n.flight().status());
        assertTrue(n.body().contains("Storm"));
    }

    @Test
    void delayNotificationUsesStatusAnnouncement() throws Exception {
        Flight f = TestData.flight("BA064");
        f.delayByMinutes(30);
        Notification n = Notification.from(new FlightEvent(Type.DELAYED, f, "On Time")).orElseThrow();
        assertInstanceOf(DelayNotification.class, n);
        assertTrue(n.body().contains("delayed"));
    }
}
