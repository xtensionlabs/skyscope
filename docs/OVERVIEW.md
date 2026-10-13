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

## 5. What you can do in the app, and what happens (handy during the demo)

### Departures board
| Action | What happens, in plain words | Concepts |
|---|---|---|
| Click the TIME / GATE / STATUS header | The flights re-order by that column. Click the same header again and the order flips. Each column knows how to compare two flights, so the board just asks it. | `Comparator` inside the `SortKey` enum (Strategy), streams, `TableView` |
| Click a flight row | A drawer slides open under the row with terminal, check-in counter, baggage belt and aircraft. Click again and it slides shut. | Click handler (lambda), `Timeline` animation, `AppState` |
| Pin a flight | That flight jumps to the top and stays there whatever you sort by, until you unpin it. | `AppState` property → `refresh()` |
| Use the keyboard (arrows, Enter, P, R) | Arrows move between rows, Enter opens the drawer, P pins, R shows the route, so the board works without a mouse. | Key event handlers, `KeyCode` |
| Watch the board on its own | Every 12 seconds the simulator moves one flight along (for example to Boarding). The status badge pops and the row flashes, with no click needed. | `SimulationService`, `Timeline`, `ScaleTransition` |
| A gate changes or a flight is delayed or cancelled | A yellow banner slides down from the top. Gate changes and cancellations show for everyone; a delay or a boarding call shows only if you pinned that flight. | Observer: `FlightEvent` → `Notification.from()` → `BannerView` |

### My Flight (search and check-in)
| Action | What happens, in plain words | Concepts |
|---|---|---|
| Type a flight number or booking reference (e.g. `BK7F3A`) and search | The app first tries it as a flight number; if that fails it tries it as a booking reference and finds that booking's flight. Spaces and lower case don't matter. | `FlightService.lookup()`, `Optional` |
| Search something that doesn't exist | A friendly "not found" message is shown instead of a crash. | `FlightNotFoundException` caught and shown |
| Look at the result card | You see the airline, status, gate, seat and a countdown (HH:MM:SS) that ticks every second until departure. | Controller clock `Timeline`, `Duration` |
| Click an "ALSO BOOKED" chip | The search runs again for the passenger's other booking. | Event handler, `bookingsOf()` |
| Click Check in | The booking is marked checked in and a boarding pass with a barcode appears. Check-in is refused once the gate has closed or the flight has left. | `Booking.checkIn()`, `Flight.canCheckIn()`, exceptions |
| Check in the same booking twice | The second attempt is refused with a message. | `BookingAlreadyCheckedInException` |

### Route to gate (map)
| Action | What happens, in plain words | Concepts |
|---|---|---|
| Click "Route to gate" (or press R) | The map screen opens and a dashed line animates from "You are here" through security and the plaza to the gate, with walking time and directions. | `GateMap.route()`, `Polyline`, `Timeline` |
| Pick another flight in the side drop-down | The route redraws to the new gate. | `ComboBox`, listener |

### Staff mode (login with `staff123`)
| Action | What happens, in plain words | Concepts |
|---|---|---|
| Staff Login | A dialog asks for the password. If it's right, a Staff Panel tab appears; if not, a "login failed" banner shows. Logout hides the tab again. | `Dialog`, `PasswordField`, `AppState.staffMode` |
| Change gate | The drop-down only lists gates that are free around that flight's departure time, so you can't pick a taken one. Everyone sees a banner and it's written in the audit log. | Command pattern, `GateOccupiedException`, Observer |
| Delay a flight | You choose a length and a reason. The estimated time moves later and shows in red on the board. A delay with no reason is refused. | `Flight.delayByMinutes()`, `InvalidFlightException` |
| Cancel a flight | You give a reason (or a default is used). The flight shows Cancelled and cannot be changed any more. | `Flight.cancel()`, final status in `FlightStatus` |
| Set a status | A staff override: forces a flight into any status you pick, skipping the normal order. (The simulator and normal moves do follow the legal order, e.g. On Time → Boarding → Gate Closed → Departed.) | `SetStatusCommand`, `FlightStatus.nextStatuses()` (polymorphism) |
| Add a flight | You fill in a form (airline, number, destination, time, gate, aircraft). Bad input such as a wrong number or a taken gate is rejected with a message; otherwise the flight appears on the board. | `AddFlightCommand`, validation in the `Flight` constructor |
| Undo last action | Reverses the latest staff action, whether a delay, cancel, gate change or an added flight, and logs it. Press again to go back further. | `Deque` stack, Memento `snapshot()` / `restore()` |
| Read the audit log | Every staff and simulator action is listed with time and who did it. | `AuditEntry` record |
| Switch Demo / Realistic | Realistic follows the real clock (boarding 25 min before departure). Demo moves flights along quickly so the board is always busy. | Strategy pattern |
| Pause / resume simulation, or trigger a gate change | Stops or restarts the 12-second timer. The trigger button moves a random flight to a free gate right now. | `Timeline.pause()` / `play()`, `triggerGateChange()` |

### Everywhere
| Action | What happens, in plain words | Concepts |
|---|---|---|
| Ctrl+F, Ctrl+1 to 4 | Jump to search, board, search again, map and staff screens. Ctrl+4 works only when logged in as staff. | `scene.getAccelerators()`, `KeyCombination` |
| Switch screen with the top tabs | One screen is shown and the others are hidden, with a short fade. | `StackPane`, `FadeTransition` |
| Aa (large text) / HC (high contrast) | The whole app grows its text or switches to strong colours. Only a CSS class is added; no screen is rebuilt. | CSS class on the root node |
| Close and reopen the app | The flights are where you left them: statuses, gates, delays and check-ins (the pin is not saved). If the file is missing or broken, the app starts with sample data instead. | `FlightDataSource`, `JsonDataSource` + Gson, try-with-resources |

---

## 6. Testing
**JUnit 5**: 65 tests in `src/test` covering `Flight` rules, status transitions, the command/undo system, gate conflicts,
notifications, strategies, the gate map and JSON loading/saving. Run: `mvnw.cmd test`.
