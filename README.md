# Skyscope – Airport Flight Information Display System

## Overview

Skyscope is a JavaFX desktop application that imitates the departure boards you see in an airport terminal.
It was built as the semester project for **ICS 1202 – Object-Oriented Programming** (Strathmore University) to show
good OOP design in a realistic setting.

It models a fictional Nairobi (NBO) airport with three terminals and 24 gates. Passengers can watch a live
departures board, look up their own flight, and find the walking route to their gate. Airport staff can log in to
add, delay, cancel or re-gate flights, with every action validated, logged and undoable.

Everything runs locally: no database or internet connection is needed. The demo flights are loaded from
`src/main/resources/data/seed.json` on first launch and the app saves its state to `data/fids.json` afterwards.

## Setup

**Requirements**

- **Git**, to download the project (<https://git-scm.com/downloads>). Check with `git --version`.
- **JDK 17 or newer** (developed on JDK 26). Check with `java -version`.
- Nothing else to install: Maven comes with the project (`mvnw` / `mvnw.cmd`).
- An internet connection is needed only the first time, so Maven can download its libraries (JavaFX, Gson, Ikonli).

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
3. Run the app:
   ```
   mvnw.cmd javafx:run          # Windows (PowerShell / Command Prompt)
   ./mvnw javafx:run            # macOS, Linux or Git Bash
   ```
4. Run the unit tests (optional):
   ```
   mvnw.cmd test                # 65 JUnit 5 tests
   ```

**Using an IDE (IntelliJ IDEA):** open the `skyscope` folder as a Maven project, wait for the dependencies to
download, then open `FidsApp.java` and click the green run arrow next to `main`.

**Staff login:** open the Staff screen and use the demo password `staff123`.

**Reset the demo data:** close the app and delete the `data/` folder. A fresh set of flights is created on the next
launch. This also happens automatically if every flight in the saved file has already departed or been cancelled.

**Troubleshooting**

- *"release version 17 not supported" or a wrong Java version:* install JDK 17 or newer and make sure `JAVA_HOME`
  points to it.
- *Maven cannot download dependencies:* check your internet connection and run the command again.
- *The window does not appear:* make sure you ran `javafx:run`, not plain `java -jar`, because JavaFX is not bundled
  into a single jar.

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
- **Persistence** – flights and bookings are saved as JSON after every change and restored on launch.
- **Accessibility** – keyboard navigation (arrows, Enter, `P` pin, `R` route, Ctrl+F search, Ctrl+1-4 screens),
  tooltips, screen-reader labels, larger-text and high-contrast modes.
- **Look** – flat black-and-yellow airport-signage style with monospace terminal-style data, real airline logos
  (`src/main/resources/logos`) and a floor-plan style terminal map.

## Architecture

| Package | Role |
|---|---|
| `model` | `Flight` (encapsulated, enforces business rules), `Aircraft` hierarchy (`NarrowBody`, `WideBody`, `RegionalJet`), `Booking`, `Passenger`, `Airline`, `FlightStatus` (enum implementing `StatusBehaviour`), `FlightDataSource` interface, in-memory `FlightRepository`, `SampleData` (reads the demo data from `resources/data/seed.json`) |
| `exception` | `FidsException` and subclasses: `FlightNotFoundException`, `InvalidGateException`, `GateOccupiedException`, `InvalidFlightException`, `BookingAlreadyCheckedInException` |
| `data` | `JsonDataSource` – Gson persistence; loading shifts times so the board resumes where it was left |
| `service` | `FlightService` (business layer, audit log, undo, observer events), `SimulationService`, `StatusTransitionStrategy` + two strategies, `GateMap` (geometry and routes), `command/*` (staff commands) |
| `notification` | `Notification` (abstract) and four subclasses, created by a factory from flight events |
| `state` | `AppState` – sort, pin, expanded row, selection, screen, staff mode, display modes, lookup query |
| `ui` | `FidsController` (presenter) and views: `HeaderBar`, `BoardView`, `LookupView`, `WayfinderView`, `AdminView`, `BannerView` |
| `resources` | `css/base.css` (components) + `css/light.css` (palette), `data/seed.json` (demo flights, airlines, destinations), `logos/` (airline logos) |

Data flow: user actions call controller methods → they mutate `AppState` or call `FlightService` → data changes
reach the controller as `FlightEvent`s (Observer) → a single `FidsController.refresh()` re-renders every view from
state + data. Views never update each other directly.

OOP and design-pattern highlights: encapsulation (`Flight`), inheritance (`Aircraft`, `Notification`,
`AdminCommand`), polymorphism (`FlightStatus` constants, strategies, data sources), interfaces
(`FlightDataSource`, `FlightListener`, `StatusTransitionStrategy`, `StatusBehaviour`), custom checked exceptions,
Observer, Strategy, Command + Memento (undo), Template Method, Factory, MVC/MVP.

See [`docs/UML.md`](docs/UML.md) for class, package, sequence and state diagrams and
[`docs/DEMO.md`](docs/DEMO.md) for a demo script.
