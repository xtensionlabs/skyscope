# Skyscope: Overview of concepts used

A JavaFX flight information display for NBO airport. Passengers view departures, search a flight, check in and
find their gate. Staff change gates, delay or cancel flights, add flights and undo actions.
**Java 17 + JavaFX + Maven + Gson + JUnit 5 (65 tests).**

## Architecture in one line
`ui` (what you see) -> `FidsController` (reacts to clicks) -> `FlightService` (rules) -> `model` (data) -> `data` (JSON file)

| Package | Job |
|---|---|
| `model` | The data: Flight, Aircraft, Booking, FlightStatus |
| `exception` | Our own errors for broken rules |
| `service` | Business rules, undo, simulation, map routes |
| `service.command` | Staff actions as objects (Command pattern) |
| `notification` | Announcement banners |
| `state` | What the user is currently doing |
| `data` | Saving and loading the JSON file |
| `ui` | JavaFX screens |

---

## 1. The four OOP pillars

| Pillar | What it means | Project implementation |
|---|---|---|
| **Encapsulation** | Bundling data and methods together while restricting direct access, to prevent accidental changes. | • All fields in `Flight` are `private`.<br>• Data is modified only through controlled methods like `changeGate()`, `delayByMinutes()` and `cancel()`.<br>• The constructor validates the data, so an invalid `Flight` object can never be created. |
| **Inheritance** | Reusing and extending code from a parent class to create child classes. | • **Domain models:** `Aircraft` is the parent of `NarrowBody`, `WideBody` and `RegionalJet`.<br>• **Exceptions:** `FidsException` is the base class of 5 custom exceptions.<br>• **Commands:** `AdminCommand` → `FlightCommand` → `DelayCommand`.<br>• **UI components:** every custom view extends a JavaFX layout (e.g. `BoardView extends VBox`). |
| **Polymorphism** | One interface, many behaviours: different classes respond to the same method call in their own way. | • Calling `aircraft.cabinCrew()` gives a different result depending on the aircraft subclass.<br>• Each constant in the `FlightStatus` enum implements its own `nextStatuses()`.<br>• The UI can display any `Notification` without knowing its exact type. |
| **Abstraction** | Hiding complex details and exposing only the essential features. | • The `FlightDataSource` interface exposes simple `load()` and `save()` operations and hides how the data is stored (JSON file or memory).<br>• Base definitions are kept abstract using abstract classes such as `Aircraft`, `AdminCommand` and `Notification`. |

---

## 2. Java language concepts

### Classes and objects
| Concept | Example | What it does |
|---|---|---|
| **Abstract class** | `Aircraft`, `AdminCommand`, `Notification` | Can't be created with `new`. Declares methods the children must write. |
| **Interface** | `FlightDataSource`, `StatusBehaviour`, `StatusTransitionStrategy`, `FlightListener` | A contract: a list of methods a class must provide. |
| **Enum** | `FlightStatus`, `FlightEvent.Type`, `AppState.Screen` | A fixed set of values. `FlightStatus` also has fields and methods, and implements an interface. |
| **Record** | `Airline`, `Passenger`, `FlightEvent`, `AuditEntry`, `Flight.Snapshot` | A short immutable data class. The constructor, getters and `equals` are auto-generated. |
| **Utility class** | `Ui`, `GateMap` | Only `static` methods and a `private` constructor, so nobody can create one. |
| **Nested / anonymous class** | `Flight.Snapshot`, the `TableRow` subclass in `BoardView` | A class inside another, or a one-off subclass with no name. |

### Keywords and members
- `private` / `protected` / `public`: access control (encapsulation).
- `final`: a value that can't change, a method that can't be overridden (`FlightCommand.execute`), or a class that can't be extended (`Ui`).
- `static`: belongs to the class, not an object (`Aircraft.of()`, `Flight.isValidGate()`).
- `super(...)`: calls the parent constructor. `this`: the current object.
- `@Override`: marks a method that replaces the parent's version.
- `default` method in an interface: `StatusTransitionStrategy.randomGateChanges()`.

### Exception handling
- Custom **checked exceptions**: `FidsException` and 5 subclasses (`InvalidFlightException`, `GateOccupiedException`, `FlightNotFoundException`, ...).
- `throw` raises the error when a rule is broken (e.g. the `Flight` constructor, `Booking.checkIn()` on a second check-in).
- `throws` in a method signature warns callers that it can fail, so they must handle it.
- `try / catch / finally` handles the error where it matters, e.g. `FidsController.runStaff()` catches it and shows the message; `finally` always refreshes the screen.
- **try-with-resources** closes the file automatically when reading or writing (`JsonDataSource`).
- Because all custom exceptions extend `FidsException`, one `catch (FidsException e)` handles every one of them.

### Collections and generics
- `List` (ordered items, e.g. bookings), `Map` (lookup by key, e.g. flight id → `Flight`), `Set` (no duplicates, e.g. allowed next statuses).
- `Deque` is the undo stack: the newest command is popped first (last in, first out).
- `LinkedHashMap` keeps insertion order and gives fast lookup. `EnumMap` / `EnumSet` are fast versions keyed by enums.
- **Generics**: the type is fixed at compile time, e.g. `List<Flight>` can only hold flights, and `TableView<Flight>` has one `Flight` per row.
- `List.copyOf(...)` returns an unmodifiable copy so outside code can't change `FlightService`'s internal data (encapsulation).

### Modern Java (Java 8 to 17)
- **Lambdas**: short anonymous functions, e.g. `e -> tick()` runs every time the timer fires, and button clicks use them too.
- **Method references**: a shortcut for a simple lambda, e.g. `Flight::scheduledTime` is used to sort flights by time.
- **Streams**: process a collection step by step, e.g. `flights.stream().filter(...).sorted(...).toList()` builds the board rows.
- **`Optional`**: a container for a value that may be missing, so we avoid `null` (`FlightService.find()`, `Notification.from()`).
- **Switch expressions**: `case X -> ...` returns a value, e.g. `Aircraft.of()` picks the subclass and `TimeAwareStrategy` picks the next status.
- **Varargs**: `String... styles` lets `Ui.label(text, "a", "b")` take any number of CSS class names.
- **Regex** (`Pattern`): validates the flight number (`KQ100`) and the gate (`B2`).
- **`java.time`**: `LocalDateTime` and `Duration` for departure times, countdowns and gate-conflict checks.

---

## 3. Design patterns

| Pattern | What it means | Project implementation |
|---|---|---|
| **Observer** | Objects subscribe to another object and are told automatically when it changes. | • `FlightService` is the subject: `addListener()` subscribes, `fire()` notifies.<br>• `FidsController implements FlightListener`, so it receives a `FlightEvent` after every change and redraws the screen. |
| **Command** | Turn an action into an object so it can be logged and undone. | • Every staff action is an `AdminCommand` with `execute()`, `undo()` and `describe()`.<br>• Subclasses: `DelayCommand`, `CancelCommand`, `ChangeGateCommand`, `SetStatusCommand`, `AddFlightCommand`.<br>• `FlightService` keeps executed commands on a stack, which powers Undo and the audit log. |
| **Template Method** | A parent fixes the steps of a process; children fill in one step. | • `FlightCommand.execute()` is `final`: find flight → save snapshot → call `apply()` → remember it.<br>• Each subclass only writes `apply()`, so undo works the same for all of them. |
| **Memento** | Save an object's state so it can be restored later. | • `Flight.snapshot()` returns a `Flight.Snapshot` record (gate, status, estimated time, reason).<br>• `Flight.restore()` puts it back; used for undo and for loading saved data. |
| **Strategy** | Make an algorithm swappable at run time. | • `StatusTransitionStrategy` has two implementations: `TimeAwareStrategy` (follows the clock) and `RandomProgressionStrategy` (fast demo).<br>• `SimulationService` uses the interface type, and the Staff Panel switches strategy with `setStrategy()`. |
| **Factory method** | A method decides which subclass to create. | • `Aircraft.of("A320")` returns the right `NarrowBody` / `WideBody` / `RegionalJet`.<br>• `Notification.from(event)` returns the matching notification subclass for an event. |
| **Dependency injection** | An object receives what it needs instead of creating it. | • `new FlightService(source)` takes any `FlightDataSource`.<br>• `new BoardView(this)` receives the controller. |
| **Layered (MVC-style)** | Separate data, rules and display. | • Model (`model`) holds data, `FlightService` holds rules, views display, and `FidsController` connects them. Views contain no business rules. |

---

## 4. JavaFX elements used

### App structure
| Element | Purpose |
|---|---|
| `Application` | Base class; `FidsApp` overrides `start()`, and `launch()` starts JavaFX |
| `Stage` / `Scene` | The window / what is shown inside it |
| `Screen`, `Rectangle2D` | Read screen size so the window fits |

### Layouts
| Element | Used for |
|---|---|
| `BorderPane` | Main layout: header on top, screens in the centre |
| `StackPane` | Pile screens on top of each other; show one at a time. Also used for the banner overlay |
| `VBox` / `HBox` | Stack children vertically / horizontally |
| `GridPane` | Grid of fields |
| `ScrollPane` | Scrollable Lookup and Staff screens |
| `Region` + `Priority` | Stretchy spacer that pushes items to the edge |
| `Group` | Wraps the map so it can be scaled to fit |

### Controls
| Element | Used for |
|---|---|
| `TableView` + `TableColumn` + `TableRow` | The departures board (each row is one `Flight`) |
| `Label`, `Button`, `ToggleButton` + `ToggleGroup` | Text, actions, navigation tabs (only one selected at a time) |
| `TextField`, `PasswordField` | Search box, flight number input, staff login |
| `ComboBox`, `ListCell`, `StringConverter` | Drop-downs and how each item is displayed as text |
| `TextFormatter` | Blocks invalid typing (for example, digits only) |
| `Dialog`, `ButtonType` | Staff login pop-up |
| `Tooltip` | Hover hints |
| `ImageView`, `Image` | Airline logos |
| `FontIcon` (Ikonli library) | Icons |

### Drawing (the terminal map)
`Rectangle` (gates, corridors), `Line` / `Polyline` / `Path` (the route), `Circle` ("You are here" and the walking dot), `Text` (labels).

### Animation
| Element | Used for |
|---|---|
| `Timeline` + `KeyFrame` + `KeyValue` | 12-second simulation tick, 1-second clock, details drawer opening, pulsing "You are here" marker |
| `FadeTransition` | Screen switch fade, blinking LIVE dot |
| `TranslateTransition` + `ParallelTransition` | Banner slides in and fades at the same time |
| `ScaleTransition`, `PauseTransition` | Status badge pop, banner wait time |

### Properties and events
- **`Property` objects** (`StringProperty`, `ObjectProperty`, `BooleanProperty`) in `AppState`: observable values that notify when they change.
- **Event handlers as lambdas**: `button.setOnAction(e -> ...)`, key handling with `KeyCode` and `KeyCombination` (Ctrl+F, Ctrl+1 to 4).
- `Platform.runLater(...)`: run something on the UI thread once it's ready.
- **CSS styling**: `getStyleClass().add("badge-bad")` with `base.css` and `light.css`. The code picks the class and CSS decides the colour. High-contrast and large-text modes are just an extra CSS class on the root.

---

## 5. Feature -> concept map (handy during the demo)

| You show... | Concepts behind it |
|---|---|
| Sort by TIME / GATE / STATUS | `Comparator` per `SortKey` enum (Strategy), streams, `TableView` |
| Click a row, drawer opens | Event handler lambda, `Timeline` animation, `AppState` property |
| Pin a flight | `AppState` property -> `refresh()` |
| Search `BK7F3A` and check in | `FlightService.lookup()`, `Optional`, `Booking.checkIn()`, exceptions on double check-in |
| Banner when a gate changes | Observer: `FlightEvent` -> `Notification.from()` -> `BannerView` |
| Route to gate | `GateMap.route()`, `Polyline`, `Timeline` animation |
| Staff change gate / delay / cancel | Command pattern, validation in `Flight`, custom exceptions |
| Undo | `Deque` stack + Memento `snapshot()`/`restore()` |
| Delay with no reason is refused | `InvalidFlightException` thrown by `Flight.delayByMinutes()` |
| Switch Demo / Realistic | Strategy pattern |
| Close and reopen, data is kept | `FlightDataSource` interface, `JsonDataSource` + Gson, try-with-resources |
| Large text / High contrast | CSS class toggled on the root node |

---

## 6. Testing
**JUnit 5**: 65 tests in `src/test` covering `Flight` rules, status transitions, the command/undo system, gate conflicts,
notifications, strategies, the gate map and JSON loading/saving. Run: `mvnw.cmd test`.
