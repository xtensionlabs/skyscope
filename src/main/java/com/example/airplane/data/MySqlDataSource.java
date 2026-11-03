package com.example.airplane.data;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** MySQL persistence for the flight board. The first empty-database load imports the old JSON file if present. */
public final class MySqlDataSource implements FlightDataSource {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/skyscope?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Nairobi";

    private final String url;
    private final String username;
    private final String password;
    private final Path legacyJson;

    public MySqlDataSource(String url, String username, String password) {
        this(url, username, password, Path.of("data", "fids.json"));
    }

    MySqlDataSource(String url, String username, String password, Path legacyJson) {
        this.url = Objects.requireNonNull(url);
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
        this.legacyJson = Objects.requireNonNull(legacyJson);
    }

    /** Uses local MySQL defaults; credentials can be supplied through environment variables. */
    public static MySqlDataSource fromEnvironment() {
        Map<String, String> env = System.getenv();
        return new MySqlDataSource(
                env.getOrDefault("SKYSCOPE_DB_URL", DEFAULT_URL),
                env.getOrDefault("SKYSCOPE_DB_USER", "root"),
                env.getOrDefault("SKYSCOPE_DB_PASSWORD", ""));
    }

    @Override
    public Snapshot load() throws IOException {
        boolean empty;
        try (Connection connection = connect()) {
            empty = rowCount(connection, "flights") == 0;
            if (!empty) {
                Snapshot snapshot = readSnapshot(connection);
                if (snapshot.flights().stream().noneMatch(f -> f.status().isActive())) {
                    Snapshot fresh = SampleData.create(LocalDateTime.now());
                    save(fresh);
                    return fresh;
                }
                return snapshot;
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }

        // One-time migration: keep gates, statuses, delay reasons, bookings and check-in flags from JSON.
        // With no old file, populate the same familiar demo set used by the current app.
        Snapshot initial = Files.exists(legacyJson)
                ? new JsonDataSource(legacyJson).loadForMigration()
                : SampleData.create(LocalDateTime.now());
        save(initial);
        try (Connection connection = connect()) {
            return readSnapshot(connection);
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public void save(Snapshot snapshot) throws IOException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                upsertReferenceData(connection, snapshot);
                clearOperationalData(connection);
                insertPassengers(connection, snapshot.bookings());
                insertFlights(connection, snapshot.flights());
                insertBookings(connection, snapshot.bookings());
                saveTimestamp(connection, LocalDateTime.now());
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    private static Snapshot readSnapshot(Connection connection) throws SQLException, IOException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime savedAt = readTimestamp(connection).orElse(now);
        Duration elapsed = Duration.between(savedAt, now);
        List<Flight> flights = readFlights(connection, elapsed);
        List<Booking> bookings = readBookings(connection);
        List<Airline> airlines = readAirlines(connection);
        List<SampleData.Destination> destinations = readDestinations(connection);
        List<String> desks = readDistinct(connection, "check_in_counter");
        List<String> belts = readDistinct(connection, "baggage_belt");
        return new Snapshot(flights, bookings, airlines, destinations, desks, belts);
    }

    private static List<Flight> readFlights(Connection c, Duration elapsed) throws SQLException, IOException {
        String sql = "SELECT f.flight_number, f.origin, f.scheduled_at, f.estimated_at, f.gate, f.status, "
                + "f.check_in_counter, f.baggage_belt, f.aircraft_key, f.status_reason, "
                + "a.airline_code, a.name AS airline_name, a.brand_color, d.city AS destination, d.airport_code "
                + "FROM flights f JOIN airlines a ON a.airline_code = f.airline_code "
                + "JOIN destinations d ON d.airport_code = f.destination_code ORDER BY f.display_order";
        List<Flight> flights = new ArrayList<>();
        try (Statement statement = c.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    FlightStatus status = FlightStatus.valueOf(rs.getString("status"));
                    LocalDateTime scheduled = rs.getObject("scheduled_at", LocalDateTime.class).plus(elapsed);
                    LocalDateTime estimated = rs.getObject("estimated_at", LocalDateTime.class).plus(elapsed);
                    Flight flight = new Flight(rs.getString("flight_number"),
                            new Airline(rs.getString("airline_code"), rs.getString("airline_name"),
                                    rs.getString("brand_color")),
                            rs.getString("origin"), rs.getString("destination"), rs.getString("airport_code"),
                            scheduled, rs.getString("gate"), status, rs.getString("check_in_counter"),
                            rs.getString("baggage_belt"), Aircraft.of(rs.getString("aircraft_key")));
                    flight.restore(new Flight.Snapshot(rs.getString("gate"), status, estimated,
                            rs.getString("status_reason")));
                    flights.add(flight);
                } catch (FidsException | IllegalArgumentException e) {
                    throw new IOException("Invalid flight row in MySQL: " + e.getMessage(), e);
                }
            }
        }
        return flights;
    }

    private static List<Booking> readBookings(Connection c) throws SQLException {
        String sql = "SELECT b.booking_reference, p.passenger_id, p.full_name, b.flight_number, b.seat, "
                + "b.boarding_group, b.checked_in FROM bookings b JOIN passengers p "
                + "ON p.passenger_id = b.passenger_id ORDER BY b.display_order";
        List<Booking> bookings = new ArrayList<>();
        Map<String, Passenger> passengers = new HashMap<>();
        try (Statement statement = c.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                Passenger passenger = passengers.computeIfAbsent(rs.getString("passenger_id"), id ->
                        new Passenger(id, getUnchecked(rs, "full_name")));
                bookings.add(new Booking(rs.getString("booking_reference"), passenger,
                        rs.getString("flight_number"), rs.getString("seat"), rs.getString("boarding_group"),
                        rs.getBoolean("checked_in")));
            }
        }
        return bookings;
    }

    // ResultSet getters throw SQLException; this helper is used only inside computeIfAbsent's lambda.
    private static String getUnchecked(ResultSet rs, String column) {
        try { return rs.getString(column); }
        catch (SQLException e) { throw new IllegalStateException(e); }
    }

    private static List<Airline> readAirlines(Connection c) throws SQLException {
        List<Airline> airlines = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT airline_code, name, brand_color FROM airlines ORDER BY display_order")) {
            while (rs.next()) airlines.add(new Airline(rs.getString(1), rs.getString(2), rs.getString(3)));
        }
        return airlines;
    }

    private static List<SampleData.Destination> readDestinations(Connection c) throws SQLException {
        List<SampleData.Destination> destinations = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT city, airport_code FROM destinations ORDER BY display_order")) {
            while (rs.next()) destinations.add(new SampleData.Destination(rs.getString(1), rs.getString(2)));
        }
        return destinations;
    }

    private static List<String> readDistinct(Connection c, String column) throws SQLException {
        if (!column.equals("check_in_counter") && !column.equals("baggage_belt")) {
            throw new IllegalArgumentException("Unexpected catalog column: " + column);
        }
        List<String> values = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT DISTINCT " + column + " FROM flights WHERE " + column + " IS NOT NULL "
                        + "AND " + column + " <> '' ORDER BY " + column)) {
            while (rs.next()) values.add(rs.getString(1));
        }
        return values;
    }

    private static Optional<LocalDateTime> readTimestamp(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT saved_at FROM app_state WHERE state_key = 'snapshot'"); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(rs.getObject(1, LocalDateTime.class)) : Optional.empty();
        }
    }

    private static int rowCount(Connection c, String table) throws SQLException {
        if (!table.equals("flights")) throw new IllegalArgumentException("Unexpected table: " + table);
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM flights")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void upsertReferenceData(Connection c, Snapshot snapshot) throws SQLException {
        LinkedHashMap<String, Airline> airlines = new LinkedHashMap<>();
        for (Airline airline : snapshot.airlines()) airlines.put(airline.code(), airline);
        LinkedHashMap<String, SampleData.Destination> destinations = new LinkedHashMap<>();
        for (SampleData.Destination destination : snapshot.destinations()) {
            destinations.put(destination.code(), destination);
        }
        for (Flight flight : snapshot.flights()) {
            airlines.putIfAbsent(flight.airline().code(), flight.airline());
            destinations.putIfAbsent(flight.destinationCode(),
                    new SampleData.Destination(flight.destination(), flight.destinationCode()));
        }

        String airlineSql = "INSERT INTO airlines (airline_code, name, brand_color, display_order) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), brand_color = VALUES(brand_color)";
        try (PreparedStatement ps = c.prepareStatement(airlineSql)) {
            int order = 0;
            for (Airline airline : airlines.values()) {
                ps.setString(1, airline.code()); ps.setString(2, airline.name()); ps.setString(3, airline.color());
                ps.setInt(4, order++); ps.addBatch();
            }
            ps.executeBatch();
        }

        String destinationSql = "INSERT INTO destinations (airport_code, city, display_order) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE city = VALUES(city)";
        try (PreparedStatement ps = c.prepareStatement(destinationSql)) {
            int order = 0;
            for (SampleData.Destination destination : destinations.values()) {
                ps.setString(1, destination.code()); ps.setString(2, destination.city());
                ps.setInt(3, order++); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void clearOperationalData(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM bookings");
            s.executeUpdate("DELETE FROM flights");
            s.executeUpdate("DELETE FROM passengers");
        }
    }

    private static void insertPassengers(Connection c, List<Booking> bookings) throws SQLException {
        String sql = "INSERT INTO passengers (passenger_id, full_name) VALUES (?, ?)";
        Set<String> inserted = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Booking booking : bookings) {
                Passenger passenger = booking.passenger();
                if (!inserted.add(passenger.id())) continue;
                ps.setString(1, passenger.id()); ps.setString(2, passenger.name()); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertFlights(Connection c, List<Flight> flights) throws SQLException {
        String sql = "INSERT INTO flights (flight_number, airline_code, origin, destination_code, scheduled_at, "
                + "estimated_at, gate, status, check_in_counter, baggage_belt, aircraft_key, status_reason, "
                + "display_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < flights.size(); i++) {
                Flight f = flights.get(i);
                ps.setString(1, f.flightNumber()); ps.setString(2, f.airline().code());
                ps.setString(3, f.origin()); ps.setString(4, f.destinationCode());
                ps.setObject(5, f.scheduledTime()); ps.setObject(6, f.estimatedTime());
                ps.setString(7, f.gate()); ps.setString(8, f.status().name());
                ps.setString(9, f.checkInCounter()); ps.setString(10, f.baggageBelt());
                ps.setString(11, f.aircraft().key()); ps.setString(12, f.statusReason());
                ps.setInt(13, i); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertBookings(Connection c, List<Booking> bookings) throws SQLException {
        String sql = "INSERT INTO bookings (booking_reference, passenger_id, flight_number, seat, boarding_group, "
                + "checked_in, display_order) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < bookings.size(); i++) {
                Booking b = bookings.get(i);
                ps.setString(1, b.bookingReference()); ps.setString(2, b.passenger().id());
                ps.setString(3, b.linkedFlightNumber()); ps.setString(4, b.seat());
                ps.setString(5, b.boardingGroup()); ps.setBoolean(6, b.isCheckedIn());
                ps.setInt(7, i); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void saveTimestamp(Connection c, LocalDateTime timestamp) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO app_state (state_key, saved_at) "
                + "VALUES ('snapshot', ?) ON DUPLICATE KEY UPDATE saved_at = VALUES(saved_at)")) {
            ps.setObject(1, timestamp);
            ps.executeUpdate();
        }
    }

    private static IOException databaseError(SQLException e) {
        return new IOException("MySQL is unavailable or the Skyscope schema is missing. "
                + "Run database/schema.sql and check SKYSCOPE_DB_URL, SKYSCOPE_DB_USER and "
                + "SKYSCOPE_DB_PASSWORD. " + e.getMessage(), e);
    }
}
