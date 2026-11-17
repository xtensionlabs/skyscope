# Wendy — Database, Persistence & Testing

**Role:** explain where SkyScope data lives, how startup restores it, and what reliability evidence the project has.
**Suggested time:** 3–4 minutes.
**Audience takeaway:** flight and booking changes are persisted through a storage abstraction, with a clear first-run data path and test coverage.

## Opening

> “A live board is only useful if staff updates and passenger check-ins survive a restart. SkyScope keeps its business logic separate from storage, then saves application data to MySQL.”

## 1. Explain the data layer

Show the flow in **docs/UML.md**, section **2. Service layer and patterns**, or use this short path:

**FidsController → FlightService → FlightDataSource → MySqlDataSource → MySQL**

- **FlightDataSource** defines the load and save contract used by FlightService.
- **MySqlDataSource** is the normal persistent implementation and uses JDBC.
- **FlightRepository** provides in-memory sample data when MySQL cannot be reached. That fallback is useful for a demo, but changes in that session are not durable.
- **JsonDataSource** supports one-time import from the older data/fids.json snapshot. It is not the normal ongoing store.

**Say:** “The service depends on the interface, not on SQL statements. That means storage can be replaced without putting database code in the screens or the flight model.”

## 2. Describe the MySQL schema

The schema is defined in **database/schema.sql**. It creates the skyscope database and six tables:

| Table | Stores | Relationship |
|---|---|---|
| airlines | Airline names, codes, and display colors | One airline can operate many flights. |
| destinations | Airport codes and city names | One destination can be used by many flights. |
| flights | Flight times, gate, status, reason, aircraft, and service details | Each flight references an airline and destination. |
| passengers | Passenger identity and name | One passenger can have multiple bookings. |
| bookings | Booking reference, flight, seat, boarding group, and check-in state | Each booking references one passenger and one flight. |
| app_state | The last saved snapshot time | Used to keep flight times moving across restarts. |

Point out that foreign keys connect flights to airlines and destinations, and bookings to passengers and flights. The booking table also prevents duplicate seats on the same flight.

## 3. Explain startup and first-run data

There are two setup stages:

1. **Create the SQL tables once.** Run database/schema.sql in MySQL before launching the application. The current application does **not** create missing MySQL tables automatically.
2. **Let the application initialize records.** When the tables exist and the flights table is empty, MySqlDataSource imports data/fids.json if that legacy file exists. If it does not, the app builds the demo records from src/main/resources/data/seed.json, then saves them to MySQL.

On later launches, the app reads the MySQL snapshot. The old JSON file remains available as a backup, but is not imported again once MySQL contains flights.

**Say:** “The first empty database is populated automatically after the schema has been installed. The schema itself is prepared with the supplied SQL script; the app handles the initial data import or seed.”

## 4. Show that changes persist

FlightService sends each successful data change through its persistence path. For example, changing a gate, adding a flight, delaying a flight, or checking in a booking causes the latest snapshot to be saved. Saving uses a database transaction: reference data and operational records are written together, then committed. A failed write is rolled back.

For a live proof, make a gate or delay change in Staff Mode, then check the corresponding row in MySQL Workbench:

    SELECT flight_number, gate, status, status_reason
    FROM flights
    WHERE flight_number = 'BA064';

To verify the passenger action after check-in:

    SELECT booking_reference, checked_in
    FROM bookings
    WHERE booking_reference = 'BK7F3A';

The app also saves when it closes. app_state.saved_at records the save time; when data is loaded, elapsed time is added to scheduled and estimated flight times so the board remains live after a restart.

**Important distinction:** flight and booking data persist. The staff undo stack and audit log are session-only, and interface state such as the pinned row is not stored in MySQL.

## 5. Cover the test evidence

The repository contains **65 JUnit 5 tests** across flight validation, status behavior, service operations, undo and commands, simulation strategies, gate mapping, notifications, and the JSON data source.

Be precise about the limit: these tests do not include a dedicated live-MySQL integration test. The MySQL path can be demonstrated by observing a saved change in Workbench or by closing and reopening the app against the same database.

To run the suite during preparation:

    mvnw.cmd test

## Close and hand off

> “The storage boundary keeps persistence out of the interface and business rules. On a clean database, the app loads the legacy snapshot or seed data, then saves normal updates to MySQL. The tests cover core rules and supporting components; for the MySQL path, we can verify the saved records directly. We’re ready for questions.”

## Setup and troubleshooting notes

- Prepare MySQL and run database/schema.sql before the presentation.
- Default connection: local MySQL at localhost:3306, database skyscope, user root, blank password. For another setup, configure SKYSCOPE_DB_URL, SKYSCOPE_DB_USER, and SKYSCOPE_DB_PASSWORD.
- If the console says MySQL is unavailable, the app has switched to in-memory sample data. Fix the connection and restart before claiming that changes persisted.
- A non-empty MySQL database takes precedence over the local JSON file. For a predictable check-in demo, make sure booking BK7F3A is not already checked in.
