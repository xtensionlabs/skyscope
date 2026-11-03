# Skyscope – Airport Flight Information Display System

## Overview

Skyscope is a JavaFX desktop application that imitates the departure boards you see in an airport terminal.
It was built as the semester project for **ICS 1202 – Object-Oriented Programming** (Strathmore University) to show
good OOP design in a realistic setting.

It models a fictional Nairobi (NBO) airport with three terminals and 24 gates. Passengers can watch a live
departures board, look up their own flight, and find the walking route to their gate. Airport staff can log in to
add, delay, cancel or re-gate flights, with every action validated, logged and undoable.

The app runs locally and stores its live flight data in MySQL. On its first database launch it imports the current
`data/fids.json` file when available, preserving saved flight and booking details. A clean install with no saved JSON
uses the existing demo set from `src/main/resources/data/seed.json`.

## Setup

**Requirements**

- **Git**, to download the project (<https://git-scm.com/downloads>). Check with `git --version`.
- **JDK 17 or newer** (developed on JDK 26). Check with `java -version`.
- **MySQL 8.0 or newer**, running locally or reachable through the configured JDBC URL.
- Nothing else to install: Maven comes with the project (`mvnw` / `mvnw.cmd`).
- An internet connection is needed the first time, so Maven can download its libraries (including MySQL Connector/J).

**Steps**

1. Download the project. Open a terminal (PowerShell on Windows), go to the folder where you want it, and clone it
   (you need access to the repository):
   ```
   git clone https://github.com/xtensionlabs/skyscope.git
   ```
   No Git? On the GitHub page click **Code → Download ZIP**, then unzip it.
2. Move into the project folder:
   ```
   cd skyscope
   ```
   Use `dir` (Windows) or `ls` to check that you can see `pom.xml`. All the commands below are run from here.
3. Create the database tables by opening `database/schema.sql` in MySQL Workbench and running it, or run it from the
   MySQL command line:
   ```
   Get-Content -Raw database/schema.sql | mysql -u root -p
   ```
   The default app settings use `root` with an empty password. For a different MySQL account, set
   `SKYSCOPE_DB_URL`, `SKYSCOPE_DB_USER` and `SKYSCOPE_DB_PASSWORD` before starting the app.
4. Run the app:
   ```
   mvnw.cmd javafx:run          # Windows (PowerShell / Command Prompt)
   ./mvnw javafx:run            # macOS, Linux or Git Bash
   ```
5. Run the unit tests (optional):
   ```
   mvnw.cmd test                # 65 JUnit 5 tests
   ```

**Using an IDE:** the project is a standard Maven project, so any of these IDEs can open it. In every IDE, make
sure the project uses JDK 17 or newer. The simplest reliable way to start the app is to run the Maven goal
`javafx:run` (the same thing the terminal command does).

- **IntelliJ IDEA:** *File → Open*, choose the `skyscope` folder (the one with `pom.xml`) and let it import as a
  Maven project. Wait for the dependencies to download, then open `FidsApp.java` and click the green run arrow next
  to `main`. If that fails, open the **Maven** panel and run *Plugins → javafx → javafx:run*.
- **Eclipse:** use *Eclipse IDE for Java Developers* (Maven support is built in). Choose *File → Import → Maven →
  Existing Maven Projects*, select the `skyscope` folder and finish. Then right-click the project and choose
  *Run As → Maven build…*, type `javafx:run` in the **Goals** box, and click **Run**. Eclipse remembers this, so next
  time use *Run As → Maven build* again.
- **Apache NetBeans:** *File → Open Project*, choose the `skyscope` folder (NetBeans shows Maven projects with a
  special icon). Right-click the project and choose *Run Maven → Goals…*, type `javafx:run`, and click **OK**.
  You can tick **Remember as** to save it for next time.

The first run in any IDE downloads the libraries, so keep your internet connection on until the window opens.

**Staff login:** open the Staff screen and use the demo password `staff123`.

**Data migration:** the first launch against an empty database imports `data/fids.json` if it exists; later launches
read and write MySQL. If that file is absent, the app fills the database from `seed.json`. The old JSON file remains
as a backup. To reset a local demo to the seed set, back up or remove `data/fids.json`, then clear the MySQL tables
(`bookings`, `flights`, `passengers`, `airlines`, `destinations`, and `app_state`) and restart the app.

**Troubleshooting**

- *"release version 17 not supported" or a wrong Java version:* install JDK 17 or newer and make sure `JAVA_HOME`
  points to it.
- *Maven cannot download dependencies:* check your internet connection and run the command again.
- *The window does not appear:* make sure you ran `javafx:run`, not plain `java -jar`, because JavaFX is not bundled
  into a single jar.
- *"JavaFX runtime components are missing" when pressing Run in Eclipse or NetBeans:* run the Maven goal
  `javafx:run` as described above instead of the plain Java run button.

## Features

- **Departures board** – dense FIDS table (flight, airline, destination, time, gate, status badge). Click the
  TIME / GATE / STATUS headers to sort, click again to reverse. Click a row (or press Enter) for a details drawer
  (terminal, check-in, baggage belt, aircraft). Pin one flight to keep it on top. Live indicator and "updated" time.
- **Live simulation** – every ~12 s a flight changes status or gate. Gate changes and cancellations show a sliding
  banner for everyone; boarding and delay announcements show for the pinned flight. Two interchangeable
  strategies: *Realistic* (follows the clock, the default) and *Demo* (fast, also shuffles gates).
- **My Flight** – search by flight number or booking reference (`BA064`, `BK7F3A`, `EK720`). Large card with a
  split-flap countdown, gate, seat and boarding group, **check-in** and a **boarding pass** with barcode. A
  passenger with several bookings gets "also booked" shortcuts.
- **Wayfinding** – terminal map drawn with JavaFX shapes, pulsing "You are here" marker and an animated route to
  the selected (or pinned) flight's gate, with walking time and directions.
- **Staff mode** – log in (demo password `staff123`) to add, delay, cancel, re-gate or force-status flights with
  validation (invalid gate, gate already occupied, illegal status change), an **undo** history, an **audit log**
  and simulation controls. Forms use drop-downs instead of typing: destinations, departure time, aircraft, check-in
  desks and baggage belts, and **only the gates that are free** at the chosen time are offered. Delays and
  cancellations carry a reason that passengers see on the board and in announcements.
- **Persistence** – flights, reference data, passengers and bookings are saved to MySQL after every change.
- **Accessibility** – keyboard navigation (arrows, Enter, `P` pin, `R` route, Ctrl+F search, Ctrl+1-4 screens),
  tooltips, screen-reader labels, larger-text and high-contrast modes.
- **Look** – flat black-and-yellow airport-signage style with monospace terminal-style data, real airline logos
  (`src/main/resources/logos`) and a floor-plan style terminal map.

## Architecture

| Package | Role |
|---|---|
| `model` | `Flight` (encapsulated, enforces business rules), `Aircraft` hierarchy (`NarrowBody`, `WideBody`, `RegionalJet`), `Booking`, `Passenger`, `Airline`, `FlightStatus` (enum implementing `StatusBehaviour`), `FlightDataSource` interface, in-memory `FlightRepository`, `SampleData` (reads the demo data from `resources/data/seed.json`) |
| `exception` | `FidsException` and subclasses: `FlightNotFoundException`, `InvalidGateException`, `GateOccupiedException`, `InvalidFlightException`, `BookingAlreadyCheckedInException` |
| `data` | `MySqlDataSource` – JDBC persistence and first-run JSON migration; `JsonDataSource` reads the old file |
| `service` | `FlightService` (business layer, audit log, undo, observer events), `SimulationService`, `StatusTransitionStrategy` + two strategies, `GateMap` (geometry and routes), `command/*` (staff commands) |
| `notification` | `Notification` (abstract) and four subclasses, created by a factory from flight events |
| `state` | `AppState` – sort, pin, expanded row, selection, screen, staff mode, display modes, lookup query |
| `ui` | `FidsController` (presenter) and views: `HeaderBar`, `BoardView`, `LookupView`, `WayfinderView`, `AdminView`, `BannerView` |
| `resources` | `css/base.css` (components) + `css/light.css` (palette), `data/seed.json` (fallback demo data), `logos/` |
| `database` | `schema.sql` creates the normalized MySQL tables and keys |

Data flow: user actions call controller methods → they mutate `AppState` or call `FlightService` → data changes
reach the controller as `FlightEvent`s (Observer) → a single `FidsController.refresh()` re-renders every view from
state + data. Views never update each other directly.

OOP and design-pattern highlights: encapsulation (`Flight`), inheritance (`Aircraft`, `Notification`,
`AdminCommand`), polymorphism (`FlightStatus` constants, strategies, data sources), interfaces
(`FlightDataSource`, `FlightListener`, `StatusTransitionStrategy`, `StatusBehaviour`), custom checked exceptions,
Observer, Strategy, Command + Memento (undo), Template Method, Factory, MVC/MVP.

See [`docs/DATABASE.md`](docs/DATABASE.md) for the project/data map and MySQL setup, [`docs/UML.md`](docs/UML.md) for
class, package, sequence and state diagrams, and [`docs/DEMO.md`](docs/DEMO.md) for a demo script.
