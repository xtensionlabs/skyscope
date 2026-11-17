# Brian — Architecture & OOP

**Role:** explain how SkyScope is organized and why the design stays maintainable.
**Suggested time:** 3–4 minutes.
**Audience takeaway:** the interface, business rules, flight model, and storage have clear responsibilities, with OOP and patterns connecting them.

## Before presenting

Open **docs/UML.md** and go to **3. Package diagram**. Keep the package structure diagram visible while you explain the flow. Use the domain and service diagrams in sections 1 and 2 only if the audience asks for more detail.

## Opening

> “You have just seen SkyScope from the passenger and staff perspectives. I’ll show how those features are organized in the code. The key idea is that the JavaFX screens do not own the flight rules or database logic.”

## 1. Walk through the layers

Point to the package diagram in **docs/UML.md** and trace one user action:

**JavaFX view → FidsController → FlightService → model → FlightDataSource → MySQL**

- **UI (ui/)** contains the board, booking lookup, wayfinding, and staff screens. Views display data and pass user actions to the controller.
- **Controller and state (FidsController, AppState)** coordinate the screens, remember interface state, and refresh the views from the latest data.
- **Service (service/)** owns application rules such as gate conflicts, check-in, simulation, event delivery, audit entries, and staff undo.
- **Model (model/)** represents flights, aircraft, bookings, passengers, and statuses. The model protects its own valid state.
- **Data (FlightDataSource, data/)** separates storage from the service. MySQL is the normal source; the in-memory repository is a fallback if MySQL is unavailable.

**Say:** “If we change the screen layout, we do not need to rewrite the flight rules. If we change the storage adapter, the service still talks to the same data-source interface. That separation keeps UI code out of the flight engine.”

## 2. Show the OOP principles in the code

### Encapsulation — model/Flight.java

Flight fields are private. Code changes a flight through methods such as changeGate, delayByMinutes, cancel, and moveTo. Those methods enforce rules and report invalid changes with domain exceptions.

**Say:** “A screen cannot quietly put a flight into an invalid state by assigning a field. It asks the Flight to perform a permitted operation.”

### Inheritance — the aircraft hierarchy

Aircraft is the shared abstract base class. NarrowBody, WideBody, and RegionalJet extend it and provide their own aircraft-specific behavior.

**Say:** “The shared aircraft details live once in the parent. Each aircraft type supplies its own category and crew requirements.”

### Polymorphism — status behavior

FlightStatus implements StatusBehaviour. Each status provides the transitions and announcement behavior for that state. Flight.moveTo asks the current status whether a requested next status is allowed.

**Say:** “The flight lifecycle is expressed through status behavior instead of a growing chain of status-specific conditionals.”

## 3. Connect the patterns to visible features

| Pattern | Where it appears | What the audience just saw |
|---|---|---|
| **Observer** | FlightService, FlightListener, FidsController | A service event reaches the controller, which refreshes the board and shows an appropriate notification. |
| **Strategy** | StatusTransitionStrategy, TimeAwareStrategy, RandomProgressionStrategy | Staff can switch between clock-based Realistic progression and fast Demo progression while the simulator keeps the same interface. |
| **Command + Memento** | service/command/, Flight.Snapshot | Staff actions are command objects. A snapshot lets the Undo button restore a changed flight; undoing a newly added flight removes it. |
| **Factory** | Aircraft.of, Notification.from | Callers ask for a suitable aircraft or notification without constructing a specific subtype themselves. |
| **Presenter-style UI (MVP-inspired)** | FidsController, AppState, JavaFX views | The controller handles actions and supplies data to views. The views do not call the database or own business rules. |

**Observer talk track:** “A flight change is handled by FlightService. It saves the data and emits a FlightEvent. FidsController listens for that event and refreshes the views.”

**Strategy talk track:** “SimulationService depends on the strategy interface. Changing the selected strategy changes the progression rules at runtime without replacing the simulator.”

**Undo talk track:** “A staff action remembers the flight’s mutable values before changing them. Undo restores that snapshot. The audit log records the action and the undo.”

## 4. Close and hand off

> “The main benefit is that each part has one job: views present information, the controller coordinates, the service enforces application rules, the model protects flight state, and the data-source interface hides storage details. Wendy will show how that data is saved and restored.”

## Quick answers

- **Does the UI talk directly to MySQL?** No. The UI calls the controller and service; the service uses the FlightDataSource abstraction.
- **How does the board know a flight changed?** FlightService notifies its listeners with a FlightEvent; the controller refreshes the views.
- **Why use a strategy for simulation?** It lets the app switch progression behavior while keeping the simulation code stable.
- **What does Undo restore?** The most recent staff command. Flight commands restore a Flight.Snapshot; the add-flight command removes the added flight.
- **Is this strict MVC?** Describe it as presenter-style or MVP-inspired separation. FidsController coordinates JavaFX views and application state; the project does not use a separate formal MVC framework.
