# Airport FIDS – Flight Information Display System

A JavaFX desktop departure board for a fictional Nairobi (NBO) airport, with personalisation, wayfinding and a
staff mode. It runs with no setup: sample data is created on first launch and saved to `data/fids.json`.

## Build, run, test

Requires JDK 17+ (developed on JDK 26). Maven is bundled via the wrapper.

```
./mvnw javafx:run        # Windows: mvnw.cmd javafx:run
./mvnw test              # 65 JUnit 5 tests
```

To reset the demo data, close the app and delete the `data/` folder.

## Features

- **Departures board** – dense FIDS table (flight, airline, destination, time, gate, status badge). Click the
  TIME / GATE / STATUS headers to sort, click again to reverse. Click a row (or press Enter) for a details drawer
  (terminal, check-in, baggage belt, aircraft). Pin one flight to keep it on top. Live indicator and "updated" time.
- **Live simulation** – every ~12 s a flight changes status or gate. Gate changes and cancellations show a sliding
  banner for everyone; boarding and delay announcements show for the pinned flight. Two interchangeable
  strategies: *Demo* (fast) and *Realistic* (follows the clock).
- **My Flight** – search by flight number or booking reference (`BA064`, `BK7F3A`, `EK720`). Large card with a
  split-flap countdown, gate, seat and boarding group, **check-in** and a **boarding pass** with barcode. A
  passenger with several bookings gets "also booked" shortcuts.
- **Wayfinding** – terminal map drawn with JavaFX shapes, pulsing "You are here" marker and an animated route to
  the selected (or pinned) flight's gate, with walking time and directions.
- **Staff mode** – log in (demo password `staff123`) to add, delay, cancel, re-gate or force-status flights with
  validation (invalid gate, gate already occupied, illegal status change), an **undo** history, an **audit log**
  and simulation controls.
- **Persistence** – flights and bookings are saved as JSON after every change and restored on launch.
- **Accessibility** – keyboard navigation (arrows, Enter, `P` pin, `R` route, Ctrl+F search, Ctrl+1-4 screens),
  tooltips, screen-reader labels, larger-text and high-contrast modes.
- **Look** – flat black-and-yellow airport-signage style, light theme.

## Architecture

| Package | Role |
|---|---|
| `model` | `Flight` (encapsulated, enforces business rules), `Aircraft` hierarchy (`NarrowBody`, `WideBody`, `RegionalJet`), `Booking`, `Passenger`, `Airline`, `FlightStatus` (enum implementing `StatusBehaviour`), `FlightDataSource` interface, in-memory `FlightRepository`, `SampleData` |
| `exception` | `FidsException` and subclasses: `FlightNotFoundException`, `InvalidGateException`, `GateOccupiedException`, `InvalidFlightException`, `BookingAlreadyCheckedInException` |
| `data` | `JsonDataSource` – Gson persistence; loading shifts times so the board resumes where it was left |
| `service` | `FlightService` (business layer, audit log, undo, observer events), `SimulationService`, `StatusTransitionStrategy` + two strategies, `GateMap` (geometry and routes), `command/*` (staff commands) |
| `notification` | `Notification` (abstract) and four subclasses, created by a factory from flight events |
| `state` | `AppState` – sort, pin, expanded row, selection, screen, staff mode, display modes, lookup query |
| `ui` | `FidsController` (presenter) and views: `HeaderBar`, `BoardView`, `LookupView`, `WayfinderView`, `AdminView`, `BannerView` |
| `resources/css` | `base.css` (components) + `light.css` (palette) |

Data flow: user actions call controller methods → they mutate `AppState` or call `FlightService` → data changes
reach the controller as `FlightEvent`s (Observer) → a single `FidsController.refresh()` re-renders every view from
state + data. Views never update each other directly.

OOP and design-pattern highlights: encapsulation (`Flight`), inheritance (`Aircraft`, `Notification`,
`AdminCommand`), polymorphism (`FlightStatus` constants, strategies, data sources), interfaces
(`FlightDataSource`, `FlightListener`, `StatusTransitionStrategy`, `StatusBehaviour`), custom checked exceptions,
Observer, Strategy, Command + Memento (undo), Template Method, Factory, MVC/MVP.

See [`docs/UML.md`](docs/UML.md) for class, package, sequence and state diagrams and
[`docs/DEMO.md`](docs/DEMO.md) for a demo script.
