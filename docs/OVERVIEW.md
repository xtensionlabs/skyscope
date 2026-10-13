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

| Pillar | What it means | Where we used it |
|---|---|---|
| **Encapsulation** | Private fields, access only through methods | `Flight`: every field is private and changes only through `changeGate()`, `delayByMinutes()`, `cancel()`. The constructor validates, so an invalid Flight can't exist. |
| **Inheritance** | A child class reuses and extends a parent | `Aircraft` -> `NarrowBody`, `WideBody`, `RegionalJet`. `FidsException` -> 5 exceptions. `AdminCommand` -> `FlightCommand` -> `DelayCommand`. Every view `extends` a JavaFX layout (`BoardView extends VBox`). |
| **Polymorphism** | Same method call, different behaviour | `aircraft.cabinCrew()` gives a different answer per subclass. Each `FlightStatus` has its own `nextStatuses()`. The UI shows any `Notification` without knowing which one. |
| **Abstraction** | Show *what*, hide *how* | `FlightDataSource` interface (`load`/`save`), abstract classes `Aircraft`, `AdminCommand`, `Notification`. |

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
- `throw` when a rule is broken (`Flight` constructor, `Booking.checkIn()`), `throws` in the method signature, `try / catch / finally` where we handle it.
- **try-with-resources** closes files automatically (`JsonDataSource`).
- One `catch (FidsException e)` handles every custom error, and the message goes straight onto the screen.

### Collections and generics
- `List`, `Map`, `Set`, `Deque` (the undo stack), `LinkedHashMap` (keeps order and gives fast lookup), `EnumMap`, `EnumSet`.
- **Generics**: `List<Flight>`, `Map<String, Flight>`, `TableView<Flight>`.
- `List.copyOf(...)` returns an unmodifiable copy so outside code can't change our data.

### Modern Java (Java 8 to 17)
- **Lambdas**: `e -> tick()`, used for button clicks and timers.
- **Method references**: `Flight::scheduledTime`, `JsonDataSource::toRec`.
- **Streams**: `flights.stream().filter(...).sorted(...).toList()`.
- **`Optional`**: a value that may be missing, so we avoid `null` (`find()`, `Notification.from()`).
- **Switch expressions** (`case X -> ...`): `Aircraft.of()`, `TimeAwareStrategy`.
- **Varargs** (`String... styles`) in `Ui.label(...)`.
- **Regex** (`Pattern`): flight number `KQ100` and gate `B2` validation.
- `java.time`: `LocalDateTime`, `Duration` for departure times and countdowns.

---

## 3. Design patterns

| Pattern | Where | In one sentence |
|---|---|---|
| **Observer** | `FlightService` + `FlightListener` + `FlightEvent` | The service tells the controller "a flight changed", so the screen updates itself. |
| **Command** | `AdminCommand`, `DelayCommand`, `CancelCommand`, `ChangeGateCommand`, `SetStatusCommand`, `AddFlightCommand` | Each staff action is an object with `execute()` and `undo()`, so undo and the audit log are easy. |
| **Template Method** | `FlightCommand.execute()` (final) | Fixed steps (find flight, save state, apply, remember), and subclasses only fill in `apply()`. |
| **Memento** | `Flight.snapshot()` / `restore()` | A saved copy of a flight's changeable values, used for undo and saving. |
| **Strategy** | `StatusTransitionStrategy` -> `TimeAwareStrategy`, `RandomProgressionStrategy` | Swap how flights progress at run time without changing the simulation code. |
| **Factory method** | `Aircraft.of(key)`, `Notification.from(event)` | A method that picks and builds the right subclass. |
| **Dependency injection** | `new FlightService(source)`, `new BoardView(this)` | Objects receive what they need instead of creating it. |
| **MVC-style layers** | model / `FlightService` / views + `FidsController` | Data, rules and display are kept separate. |

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
