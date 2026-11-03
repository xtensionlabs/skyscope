-- Skyscope demo database. Run this once in MySQL before starting the app.
CREATE DATABASE IF NOT EXISTS skyscope
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE skyscope;

CREATE TABLE IF NOT EXISTS airlines (
    airline_code CHAR(2) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    brand_color CHAR(7) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS destinations (
    airport_code CHAR(3) PRIMARY KEY,
    city VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS passengers (
    passenger_id VARCHAR(20) PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS flights (
    flight_number VARCHAR(8) PRIMARY KEY,
    airline_code CHAR(2) NOT NULL,
    origin VARCHAR(100) NOT NULL,
    destination_code CHAR(3) NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    estimated_at DATETIME(6) NOT NULL,
    gate VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    check_in_counter VARCHAR(30),
    baggage_belt VARCHAR(20),
    aircraft_key VARCHAR(30) NOT NULL,
    status_reason VARCHAR(255) NOT NULL DEFAULT '',
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_flights_airline FOREIGN KEY (airline_code)
        REFERENCES airlines (airline_code),
    CONSTRAINT fk_flights_destination FOREIGN KEY (destination_code)
        REFERENCES destinations (airport_code),
    CONSTRAINT chk_flight_status CHECK (status IN
        ('ON_TIME', 'BOARDING', 'DELAYED', 'GATE_CLOSED', 'DEPARTED', 'CANCELLED')),
    INDEX idx_flights_departure (scheduled_at),
    INDEX idx_flights_status (status),
    INDEX idx_flights_gate_departure (gate, scheduled_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS bookings (
    booking_reference VARCHAR(20) PRIMARY KEY,
    passenger_id VARCHAR(20) NOT NULL,
    flight_number VARCHAR(8) NOT NULL,
    seat VARCHAR(8) NOT NULL,
    boarding_group VARCHAR(20) NOT NULL,
    checked_in BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_bookings_passenger FOREIGN KEY (passenger_id)
        REFERENCES passengers (passenger_id),
    CONSTRAINT fk_bookings_flight FOREIGN KEY (flight_number)
        REFERENCES flights (flight_number),
    CONSTRAINT uq_booking_flight_seat UNIQUE (flight_number, seat),
    INDEX idx_bookings_passenger (passenger_id)
) ENGINE = InnoDB;

-- The app updates this after each saved snapshot to preserve the live-time offset on restart.
CREATE TABLE IF NOT EXISTS app_state (
    state_key VARCHAR(30) PRIMARY KEY,
    saved_at DATETIME(6) NOT NULL
) ENGINE = InnoDB;
