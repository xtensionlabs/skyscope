package com.example.airplane.data;

import com.example.airplane.model.Booking;
import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightDataSource.Snapshot;
import com.example.airplane.model.FlightStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JsonDataSourceTest {
    @TempDir
    Path dir;

    private static Flight byId(Snapshot s, String id) {
        return s.flights().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void firstLoadSeedsSampleDataAndCreatesFile() throws IOException {
        Path file = dir.resolve("nested").resolve("fids.json");
        Snapshot s = new JsonDataSource(file).load();
        assertTrue(Files.exists(file));
        assertEquals(22, s.flights().size());
        assertEquals(6, s.bookings().size());
    }

    @Test
    void changesSurviveAReload() throws Exception {
        Path file = dir.resolve("fids.json");
        JsonDataSource ds = new JsonDataSource(file);
        Snapshot s1 = ds.load();
        byId(s1, "KQ412").changeGate("A4");
        byId(s1, "BA064").delayByMinutes(20, "Test reason");
        byId(s1, "EK720").cancel("Crew shortage");
        s1.bookings().get(0).checkIn();
        ds.save(s1);

        Snapshot s2 = new JsonDataSource(file).load();
        assertEquals("A4", byId(s2, "KQ412").gate());
        assertEquals(FlightStatus.DELAYED, byId(s2, "BA064").status());
        assertEquals("Crew shortage", byId(s2, "EK720").statusReason());
        Booking b = s2.bookings().get(0);
        assertTrue(b.isCheckedIn());
        assertEquals("B787-8", byId(s2, "BA064").aircraft().key());
        assertEquals("Amina Wanjiru", b.passengerName());
    }

    @Test
    void passengersWithTwoBookingsShareOneObject() throws IOException {
        Path file = dir.resolve("fids.json");
        JsonDataSource ds = new JsonDataSource(file);
        ds.save(ds.load());
        Snapshot s = new JsonDataSource(file).load();
        Booking first = s.bookings().get(0);
        Booking second = s.bookings().get(1);
        assertSame(first.passenger(), second.passenger());
    }

    @Test
    void timesAreShiftedByTimeSinceSave() throws IOException {
        Path file = dir.resolve("fids.json");
        JsonDataSource ds = new JsonDataSource(file);
        Snapshot original = ds.load();
        LocalDateTime scheduled = byId(original, "BA064").scheduledTime();

        // Pretend the file was saved two hours ago.
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String patched = text.replaceAll("\"savedAt\": \"[^\"]+\"",
                "\"savedAt\": \"" + LocalDateTime.now().minusHours(2) + "\"");
        Files.writeString(file, patched, StandardCharsets.UTF_8);

        LocalDateTime reloaded = byId(new JsonDataSource(file).load(), "BA064").scheduledTime();
        long minutes = Duration.between(scheduled, reloaded).toMinutes();
        assertTrue(Math.abs(minutes - 120) <= 1, "expected ~120 minutes shift but was " + minutes);
    }

    @Test
    void corruptFileIsReportedAsIOException() throws IOException {
        Path file = dir.resolve("fids.json");
        Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new JsonDataSource(file).load());
    }

    @Test
    void emptyObjectIsReportedAsIOException() throws IOException {
        Path file = dir.resolve("fids.json");
        Files.writeString(file, "{}", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new JsonDataSource(file).load());
    }

    @Test
    void unknownStatusIsReportedAsIOException() throws IOException {
        Path file = dir.resolve("fids.json");
        JsonDataSource ds = new JsonDataSource(file);
        ds.load();
        String text = Files.readString(file, StandardCharsets.UTF_8).replace("\"ON_TIME\"", "\"SIDEWAYS\"");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new JsonDataSource(file).load());
    }
}
