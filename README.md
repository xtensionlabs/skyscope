# Airport FIDS – Flight Information Display System

A JavaFX desktop departure board with personalisation and wayfinding. Sample data is hard-coded
(Nairobi NBO departures, times relative to launch), so it runs with no setup.

## Build & run

Requires JDK 17+ (developed on JDK 26). Maven is bundled via the wrapper.

```
./mvnw javafx:run        # Windows: mvnw.cmd javafx:run
```

## Features

- **Departures board** – dense FIDS table (flight, airline, destination, time, gate, status badge).
  Click the TIME / GATE / STATUS headers to sort; click again to reverse (arrow shows direction).
  Click a row to open a details drawer (terminal, check-in, baggage belt). Pin icon keeps one flight on top.
- **Live simulation** – every ~12 s a random flight changes status (On Time → Boarding → Gate Closed →
  Departed, occasionally Delayed) or gate. Gate changes show a sliding banner that auto-dismisses.
- **My Flight** – search by flight number or booking reference (try `BA064`, `BK7F3A`, `EK720`) for a large card
  with a split-flap countdown, gate, terminal, status, seat and boarding group.
- **Wayfinding** – terminal map drawn with JavaFX shapes, pulsing "You are here" marker and an animated route to
  the selected (or pinned) flight's gate. Selecting a row on the board updates the route.
- **Airport signage theme** – light, flat, black and yellow, high contrast.

## Architecture

| Package | Role |
|---|---|
| `model` | `Flight`, `Booking` (record), `Airline` (record), `FlightStatus`, `FlightRepository` (sample data + lookup) |
| `state` | `AppState` – sort key/direction, pinned flight, theme, expanded row, selected flight, screen, lookup query |
| `service` | `SimulationService` (Timeline-driven live updates), `GateMap` (map geometry + route calculation) |
| `ui` | `FidsController` (presenter) and views: `HeaderBar`, `BoardView`, `LookupView`, `WayfinderView`, `BannerView` |
| `resources/css` | `base.css` (components) + `dark.css` / `light.css` (colour palettes) |

Data flow: user actions call controller methods → they mutate `AppState` → any state change (and every
simulation event) calls the single `FidsController.refresh()`, which re-renders all views from state + data.
Views never update each other directly.
