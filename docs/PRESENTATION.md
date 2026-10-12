# Presentation guide (3 presenters)

Skyscope is a flight information display system for NBO airport. The code is split into layers, and each presenter
explains one layer. Every presenter should know the **big picture** below, then go deep on their own part.

## The big picture (everyone says this in 30 seconds)

```
 UI (JavaFX views)  -->  FidsController  -->  FlightService  -->  Flight / Aircraft / Booking   (model)
   what you see          reacts to clicks     the business rules          the data + its own rules
                                                    |
                                                    +--> FlightDataSource (interface) --> JsonDataSource / FlightRepository
```

- The **model** holds the data and protects it (a Flight can't be created or changed in an illegal way).
- The **service** applies the rules, saves the data, and tells everyone when something changed.
- The **UI** only displays things and passes clicks on. It contains no business rules.

Run it: `mvnw.cmd javafx:run` (Windows) or `./mvnw javafx:run`. Tests: `mvnw.cmd test`. Demo script: `docs/DEMO.md`.

## Who explains what

| Person | Part | Folders / files | About |
|---|---|---|---|
| 1 | Model + data | `model/`, `exception/`, `data/` | ~730 lines |
| 2 | Logic | `service/`, `service/command/`, `notification/`, `state/` | ~900 lines |
| 3 | UI | `ui/`, `FidsApp.java`, `resources/css` | ~2,000 lines |

Suggested order on the day: Person 1, then Person 2, then Person 3 (data, then rules, then screen).
Person 3 finishes with the live demo.

---

## Person 1: Model, exceptions, data

Open the files in this order.

**1. `model/Aircraft.java` (abstract class) + `NarrowBody`, `WideBody`, `RegionalJet`**
- `Aircraft` is **abstract**: you can't write `new Aircraft(...)`. It declares `category()` and `cabinCrew()` with no body.
- Each subclass **extends** it and **overrides** those methods. A WideBody needs at least 8 crew, a NarrowBody at least 3.
- That is **inheritance** (`extends`, `super(...)`) and **polymorphism** (same method call, different answer per subclass).
- `Aircraft.of("A320")` is a **factory method**: it builds the right subclass from a code.
- Fields are `private final` and read through getters: **encapsulation**.

**2. `model/Flight.java`**
- Private fields, getters, and no setters. Changes only happen through methods like `changeGate`, `delayByMinutes`, `cancel`.
- The **constructor validates everything** and throws `InvalidFlightException` / `InvalidGateException`, so an invalid Flight can never exist.
- A Flight **has-a** `Airline` and **has-a** `Aircraft`: **composition**.
- `snapshot()` / `restore()` save and put back the changeable values. This is the **Memento pattern**, used for undo and for saving.
- `terminal()` is worked out from the gate letter instead of stored, so it can never disagree with the gate.

**3. `model/FlightStatus.java` + `StatusBehaviour.java`**
- `FlightStatus` is an **enum that implements an interface** (`StatusBehaviour`).
- Each constant (ON_TIME, BOARDING, ...) has its **own** `nextStatuses()` and `announcement()`. That is where the legal flow comes from: On Time -> Boarding -> Gate Closed -> Departed. Departed and Cancelled are final.
- `Flight.moveTo()` just asks the status "may I go there?" instead of using a long if/else chain.

**4. `model/Booking.java`, `Passenger`, `Airline`**
- `Airline` and `Passenger` are **records** (short immutable data classes).
- `Booking.checkIn()` throws `BookingAlreadyCheckedInException` if you check in twice.

**5. `exception/`**
- `FidsException extends Exception`, and the other 5 exceptions extend `FidsException`. One `catch (FidsException e)` handles all of them. These are **custom checked exceptions**.

**6. `model/FlightDataSource.java` (interface), `FlightRepository.java`, `data/JsonDataSource.java`**
- The interface says only `load()` and `save()`. Two classes implement it: memory-only (`FlightRepository`) and JSON file (`JsonDataSource`, using Gson).
- The rest of the app doesn't know which one it is using: **abstraction + polymorphism**. `FidsController.createService()` falls back to the in-memory one if the file can't be read.
- `JsonDataSource.load()` shifts all times by the time since the last save, so the board always looks "live".

**Likely questions for Person 1**
- *Where is inheritance?* `Aircraft` -> `NarrowBody`/`WideBody`/`RegionalJet`, and `FidsException` -> the 5 exceptions.
- *Where is polymorphism?* `category()`/`cabinCrew()` per aircraft, `nextStatuses()`/`announcement()` per status, and `load()`/`save()` per data source.
- *Where is encapsulation?* Every private field in `Flight`; the only way to change it is a checked method.
- *Abstract class vs interface?* Abstract class (`Aircraft`) shares fields and code between related classes. Interface (`FlightDataSource`) is a pure contract that unrelated classes can sign.
- *Why a record for Airline?* It is just immutable data. The constructor, getters and `equals` are generated.
- *Why custom exceptions?* They give clear messages the UI can show directly (for example "Gate B2 is already occupied by KQ 100").

---

## Person 2: Service, patterns, notifications, state

**1. `service/FlightService.java`** (the centre of the app)
- Owns the flights, bookings, audit log and undo history.
- Every change follows the same steps: validate -> change the flight -> `fire(...)` (which saves, then tells listeners).
- Gate rule: two active flights can't use a gate within 40 minutes of each other (`ensureGateFree`, `GateOccupiedException`).
- `persist()` doesn't throw if saving fails: it stores the error so the app keeps running.

**2. Observer pattern: `FlightListener`, `FlightEvent`**
- `FlightService` is the **subject**: `addListener(...)` subscribes, `fire(...)` notifies.
- `FlightListener` is the interface. `FidsController implements FlightListener`, so it receives each `FlightEvent`.
- `FlightEvent` is a record: what happened (the `Type` enum), which flight, and the old value.

**3. Command pattern: `service/command/`**
- `AdminCommand` (abstract): `execute()`, `undo()`, `describe()`.
- `FlightCommand` is a **Template Method**: `execute()` is `final`. It finds the flight, saves a snapshot, then calls the subclass's `apply()`. This is why undo works the same for every command.
- `DelayCommand`, `CancelCommand`, `ChangeGateCommand`, `SetStatusCommand` only supply `apply()`. `AddFlightCommand` extends `AdminCommand` directly because the flight doesn't exist yet. Its undo removes it.
- `FlightService.execute(cmd, actor)` runs it, pushes it on a stack (`history`) and writes the audit log. `undoLast()` pops the newest. **Stack = last in, first out.**

**4. Strategy pattern: `StatusTransitionStrategy`**
- Interface with `next(flight, now, rnd)`. Two implementations:
  - `TimeAwareStrategy` ("Realistic"): boarding opens 25 min before departure, gate closes 5 min before, departs on time.
  - `RandomProgressionStrategy` ("Demo"): moves flights along quickly, ignoring the clock.
- `SimulationService` holds the strategy as the **interface type**, and the Admin screen swaps it at run time with `setStrategy(...)`. It calls `service.moveStatus(...)`, so simulated changes obey the same rules as staff changes.

**5. `notification/`**
- `Notification` is abstract. `DelayNotification`, `GateChangeNotification`, `CancellationNotification` and `BoardingCallNotification` each override `heading()` and `body()`. Some override `isBroadcast()`.
- `Notification.from(event)` is a **factory method**: it picks the subclass from the event type. The UI works with `Notification` and never needs to know the subclass.

**6. `state/AppState.java`**
- One object holds what the user is doing (current screen, sort column, pinned flight, staff mode, and so on) as JavaFX `Property` objects. `onChange(...)` runs the controller's `refresh()` whenever any of them changes.
- `SortKey` is an enum where each constant carries its own `Comparator` (another small Strategy).

**7. `service/GateMap.java`**
- Pure geometry: gate positions, the route from "You are here" to a gate, and distance in metres. Static methods only, with a private constructor.

**Likely questions for Person 2**
- *Which design patterns did you use?* Observer, Command (with Template Method and Memento), Strategy, Factory Method.
- *How does undo work?* Each command stores a snapshot of the flight before it runs. Undo restores it, or removes the flight for `AddFlightCommand`.
- *Why Strategy for the simulation?* We can swap the behaviour at run time without changing `SimulationService`.
- *Why does the simulation go through FlightService?* One place for the rules, so simulation, staff and UI can't break them.
- *Difference between Observer and Command?* Observer is "tell me when something happens". Command is "do this action, and be able to undo it".
- *What does `final` on `FlightCommand.execute` do?* Subclasses can't override it, so the find -> snapshot -> apply order is guaranteed.

---

## Person 3: UI and the live demo

The UI is JavaFX. The views are **built in Java code** (no FXML), and the look comes from `resources/css/base.css` and `light.css`.

**1. `FidsApp.java`**
- `extends Application` and **overrides** `start()`. It sizes the window, creates the controller and shows the Stage. `main()` calls `launch()`.

**2. `ui/FidsController.java`** (the "presenter")
- Creates the service, state, simulation and all views, and passes `this` to each view so they can call back.
- User actions (`sortBy`, `togglePin`, `checkIn`, `runStaff`, ...) only **change state or call the service**.
- `refresh()` is the **single redraw method**: it re-renders every view from state + data. `batch(...)` groups several state changes into one redraw.
- `onFlightEvent(...)` is the Observer callback from Person 2's part. It shows a banner and refreshes.
- Staff login is a simple demo password (`staff123`).
- Keyboard shortcuts: Ctrl+F (search), Ctrl+1 to 4 (screens).

**3. The views** (each one `extends` a JavaFX container, which is inheritance)
| File | Extends | Shows |
|---|---|---|
| `HeaderBar` | `HBox` | Clock, navigation tabs, staff button, accessibility toggles |
| `BoardView` | `VBox` | Departures table, sortable headers, pinning, expandable details drawer |
| `LookupView` | `ScrollPane` | Search by flight or booking, countdown tiles, check-in, boarding pass |
| `WayfinderView` | `HBox` | Terminal map, animated route to the gate, walking time |
| `AdminView` | `ScrollPane` | Staff: change gate, delay, cancel, add flight, undo, simulation strategy, audit log |
| `BannerView` | `HBox` | The sliding announcement banner |
| `Ui` | (utility) | Static helpers that create labels, buttons, badges and logos |

- Every view has an `update(...)` method that the controller calls from `refresh()`. Views never talk to the service directly.
- `Ui` is a utility class (all `static`, private constructor), to avoid repeating "create control + add CSS class".
- The accessibility modes (large text, high contrast) just add a CSS class to the root.

**4. Live demo**: follow `docs/DEMO.md` (about 6 minutes). Use these trigger points to link back to your teammates:
- Change a gate in the Staff Panel: *"This runs a ChangeGateCommand (Person 2), which asks the Flight to change (Person 1), and then the Observer fires and the banner appears."*
- Delay with no reason: *"Refused by Flight.delayByMinutes, which throws InvalidFlightException (Person 1)."*
- Press Undo: *"The Memento snapshot is restored."*
- Close and reopen the app: *"JsonDataSource saved and loaded it (Person 1)."*

**Likely questions for Person 3**
- *How does the UI know when data changes?* `FlightService` fires a `FlightEvent`. `FidsController` is a `FlightListener`, so it calls `refresh()`.
- *Why do views not call the service directly?* It keeps the UI free of business rules, so rules live in one place.
- *Why is `refresh()` one method?* Every screen always shows the same data, and there is nothing to forget to update.
- *What's `this` in `new BoardView(this)`?* The controller is passed in (dependency injection) so the view can report clicks.
- *Where is inheritance in the UI?* Each view extends a JavaFX layout class, and `FidsApp` extends `Application`.
- *What is the anonymous inner class in `BoardView`?* A `TableRow` subclass that overrides `updateItem` to style each row.

---


