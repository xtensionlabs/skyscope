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

## 5. What happens in the code when you use the app (handy during the demo)

**The pattern behind almost every action:** a view's click handler calls a method on `FidsController`. The controller
either changes `AppState` or asks `FlightService` to do the work. `FlightService` checks the rules, changes the
`Flight`, saves to JSON and fires a `FlightEvent`. The controller hears that event (Observer) and calls `refresh()`,
which redraws every screen from the latest data. The views never change data themselves.

### Departures board
| Action | What happens in the code |
|---|---|
| Click the TIME / GATE / STATUS header | The header's click handler calls `controller.sortBy(key)`. The controller updates `state.sortKey` (or flips `state.ascending` if it's the same column). Changing a property triggers `refresh()`, which calls `boardRows()`. That takes the comparator stored inside the chosen `SortKey` enum constant and sorts the flights with a stream. `board.update()` then redraws the table. |
| Click a flight row | The row's click handler calls `controller.onRowClicked(id)`. This sets `state.expandedFlightId` and `state.selectedFlightId` inside `batch(...)`, so the screen redraws once. `refresh()` passes the expanded flight to `BoardView.update()`, which runs a `Timeline` that grows the drawer's height from 0 to full size. Clicking again sets the id to null and the drawer shrinks. |
| Pin a flight (pin icon or P key) | The click or key handler calls `controller.togglePin(id)`, which sets `state.pinnedFlightId` (or clears it if it was already pinned). `refresh()` runs `boardRows()` again, and its sort puts the pinned id first before applying the normal order. The pin lives only in `AppState`, so it is not saved to the file. |
| Arrow keys, Enter, P, R | `table.setOnKeyPressed` reads the `KeyCode`. Arrows are handled by `TableView` itself. Enter or Space calls `onRowClicked`, P calls `togglePin`, R calls `showRoute`. `e.consume()` tells JavaFX the key is handled. |
| The board changes by itself | `SimulationService` has a `Timeline` that calls `tick()` every 12 seconds. `tick()` takes the active flights, asks the current `StatusTransitionStrategy` what the next status should be, and calls `service.moveStatus()` (or `service.delay()`). `FlightService` then calls `flight.moveTo()`, saves, and fires a `FlightEvent`. `FidsController.onFlightEvent()` runs, sets `flashId` and calls `refresh()`, so the changed row's badge pops. |
| A banner slides in | `onFlightEvent()` passes the event to `Notification.from(e)`, a factory that returns a `GateChangeNotification`, `DelayNotification`, `CancellationNotification` or `BoardingCallNotification` (or nothing). The controller keeps it if `isBroadcast()` is true or it's the pinned flight, then calls `banner.show(n.heading(), n.body())`. `BannerView` plays slide, wait 6 seconds, slide out. |

### My Flight (search and check-in)
| Action | What happens in the code |
|---|---|
| Search a flight number or booking reference | Enter or the Search button calls `controller.search(text)`, which sets `state.lookupQuery`. `refresh()` then calls `service.lookup(q)`. That cleans the text (`normalise`: remove spaces, upper-case), tries it as a flight id in the map, and if that fails loops through the bookings to match a reference and returns its flight. `refresh()` also calls `bookingMatching()` and `bookingsOf()`, then passes everything to `lookup.update()`, which builds the card. |
| Search something that doesn't exist | `service.lookup()` throws `FlightNotFoundException`. `refresh()` catches it and leaves `found` as null. `LookupView` sees no result and builds the "not found" message instead of a card. |
| The countdown ticks | The controller owns a 1-second `Timeline`. Each tick calls `lookup.tick()`, which uses `Ui.countdown()` on the flight's estimated time and writes the digits into the six tile labels. |
| Check in | The Check in button calls `controller.checkIn(reference)`, which calls `service.checkIn()`. The service finds the booking, gets its flight, checks `flight.canCheckIn()`, then calls `booking.checkIn()` and fires an event. The controller stores a success message (or `e.getMessage()` if a `FidsException` was thrown) in `lookupNotice` and calls `refresh()`. |
| Check in twice | `Booking.checkIn()` sees `checkedIn` is already true and throws `BookingAlreadyCheckedInException`. It travels up through `FlightService` to the controller's `catch (FidsException e)`, and its message is shown on the card. |
| Click an "ALSO BOOKED" chip | The handler puts the other reference in the search box and calls `controller.search()`, so it is just a new search. |

### Route to gate (map)
| Action | What happens in the code |
|---|---|
| Route to gate button (or R) | The handler calls `controller.showRoute(id)`, which sets `selectedFlightId` and `state.screen = MAP` in one `batch`. `refresh()` calls `routeTarget()` and then `wayfinder.update(target, flights)`. |
| The route is drawn | `WayfinderView.update()` calls `GateMap.route(gate)`, which returns the corner points (You are here → security → plaza → corridor → gate). The view puts them into a `Polyline` for the dashed line and a `Path` for the walking dot. One `Timeline` slides the dash offset so the dashes flow, and a `PathTransition` moves a `Circle` along the path. |
| Walking time and distance | `GateMap.lengthMetres()` adds up the distance between points with `Math.hypot`. The view divides by the walking speed to get minutes. |
| Pick another flight in the drop-down | The `ComboBox` listener calls `controller.selectFlight(id)`, which sets `selectedFlightId` and triggers `refresh()`. The `updating` flag stops the listener firing when the code sets the picker itself. |

### Staff mode (login with `staff123`)
| Action | What happens in the code |
|---|---|
| Staff Login | The button calls `controller.staffButtonPressed()`, which builds a `Dialog` with a `PasswordField` and waits with `showAndWait()`. A correct password sets `state.staffMode = true` and switches to the ADMIN screen. `HeaderBar.update()` shows the Staff Panel tab only while `staffMode` is true. A wrong one calls `banner.show("LOGIN FAILED", ...)`. |
| Any staff button (the common path) | The handler calls `withSelected(f -> new XCommand(...))`. That builds an `AdminCommand` for the selected flight and calls `controller.runStaff(cmd)`, then `service.execute(cmd, "STAFF")`. The service runs `cmd.execute()`, pushes it on the `history` stack, and writes `describe()` to the audit log. If anything throws a `FidsException`, the controller returns the message to show. `finally` always calls `refresh()`. |
| Change gate | Builds a `ChangeGateCommand`, whose `apply()` calls `service.changeGate()`. That checks the gate name, calls `ensureGateFree()` (loops over active flights, throws `GateOccupiedException` if one is within 40 minutes), calls `flight.changeGate()`, and fires a `GATE_CHANGED` event, which causes the banner. The gate drop-down is filled by `service.availableGates()`, so occupied gates are never offered. |
| Delay | Builds a `DelayCommand`. `service.delay()` calls `flight.delayByMinutes()`, which rejects 0 or over 720 minutes, a blank reason, or a status that can't be delayed (all `InvalidFlightException`). Otherwise it adds the minutes to `estimatedTime`, stores the reason and sets the status to `DELAYED`. The view also checks for a blank reason before building the command. |
| Cancel | Builds a `CancelCommand`. `flight.cancel()` refuses if the flight is already departed or cancelled, uses "Operational reasons." if the reason is blank, and sets `CANCELLED`. `CANCELLED.nextStatuses()` is empty, so `moveTo()` can't move it out of that state. |
| Force status | Builds a `SetStatusCommand`, which calls `service.forceStatus()` → `flight.forceStatus()`. That skips the `nextStatuses()` check, which is why it's an override. |
| Add a flight | `AdminView.addFlight()` reads the form and does `new Flight(...)`, so the constructor's validation runs (flight number regex, gate regex, null checks). It wraps the flight in an `AddFlightCommand`; `service.addFlight()` rejects duplicate ids and taken gates, puts it in the map, and fires `ADDED`. |
| Undo | The button calls `controller.undoStaff()` → `service.undoLast()`. It pops the newest command off the `Deque` and calls `cmd.undo()`. For flight commands, `FlightCommand.undo()` calls `service.restore(flight, before)`, which applies the saved `Snapshot` (Memento). For `AddFlightCommand`, undo calls `removeFlight()`. The undo is logged as "UNDO: ...". |
| Audit log | `service.record(actor, text)` adds an `AuditEntry`. `refresh()` passes `service.auditLog()` (a reversed copy, so newest first) to `admin.update()`. |
| Switch Demo / Realistic | The strategy `ComboBox` handler calls `simulation.setStrategy(...)`. The next `tick()` calls `strategy.next()` on the new object. The simulation only knows the interface, so no other code changes. |
| Pause or resume, trigger gate change | The toggle calls `simulation.setRunning()`, which does `timeline.play()` or `pause()`. The trigger button calls `triggerGateChange()`, which picks a random flight that `canChangeGate()` and tries up to 10 random gates on its pier through `service.changeGate()`. |

### Everywhere
| Action | What happens in the code |
|---|---|
| Ctrl+F, Ctrl+1 to 4 | `controller.start()` registers each combination with `scene.getAccelerators()`. Each one runs a lambda that calls `showScreen(...)`. Ctrl+4 first checks `state.staffMode`. |
| Switch screens with the tabs | A tab click calls `controller.showScreen(s)`, which sets `state.screen`. `showScreen()` inside `refresh()` sets `setVisible/setManaged` so only one screen in the `StackPane` shows, then plays a 220 ms `FadeTransition`. |
| Aa / HC buttons | Call `toggleLargeText()` / `toggleHighContrast()`, which flip a `BooleanProperty`. `refresh()` calls `applyDisplayModes()`, which adds or removes the `large-text` / `high-contrast` class on the root, and the CSS does the rest. |
| Close and reopen | `FidsApp.stop()` calls `controller.stop()`, which stops the simulation and calls `service.persist()`. `FlightService` also saves after every change. On the next start, `createService()` builds a `JsonDataSource`; `load()` reads the file with Gson, rebuilds `Flight` and `Booking` objects, and shifts every time forward by the time that has passed. If the file is missing it is created from `SampleData`; if it is broken, `createService()` falls back to the in-memory `FlightRepository`. |

---

## 6. Testing
**JUnit 5**: 65 tests in `src/test` covering `Flight` rules, status transitions, the command/undo system, gate conflicts,
notifications, strategies, the gate map and JSON loading/saving. Run: `mvnw.cmd test`.
