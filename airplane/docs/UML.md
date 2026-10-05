# UML diagrams

All diagrams are [Mermaid](https://mermaid.js.org/), which GitHub renders directly. Paste the code blocks into
<https://mermaid.live> to export them as images for the report.

## 1. Domain model (class diagram)

```mermaid
classDiagram
    class Flight {
        -String flightNumber
        -LocalDateTime scheduledTime
        -LocalDateTime estimatedTime
        -String gate
        -FlightStatus status
        -String cancellationReason
        +changeGate(String) String
        +moveTo(FlightStatus)
        +delayByMinutes(int)
        +cancel(String)
        +canChangeGate() boolean
        +canCheckIn() boolean
        +isBoardable() boolean
        +terminal() String
        +snapshot() Snapshot
        +restore(Snapshot)
    }
    class Airline {
        <<record>>
        code
        name
        color
    }
    class Aircraft {
        <<abstract>>
        -String key
        -int seats
        +category()* String
        +cabinCrew()* int
        +of(String)$ Aircraft
    }
    class NarrowBody
    class WideBody
    class RegionalJet
    class Booking {
        -String bookingReference
        -String seat
        -String boardingGroup
        -boolean checkedIn
        +checkIn()
    }
    class Passenger {
        <<record>>
        id
        name
    }
    class StatusBehaviour {
        <<interface>>
        +nextStatuses() Set
        +announcement(Flight) String
    }
    class FlightStatus {
        <<enumeration>>
        ON_TIME
        BOARDING
        DELAYED
        GATE_CLOSED
        DEPARTED
        CANCELLED
    }
    Aircraft <|-- NarrowBody
    Aircraft <|-- WideBody
    Aircraft <|-- RegionalJet
    StatusBehaviour <|.. FlightStatus
    Flight "*" --> "1" Airline
    Flight "*" --> "1" Aircraft
    Flight --> "1" FlightStatus
    Booking "*" --> "1" Passenger : belongs to
    Booking "*" ..> "1" Flight : linked by flight number
```

## 2. Service layer and patterns (class diagram)

```mermaid
classDiagram
    class FlightService {
        +lookup(String) Flight
        +changeGate(Flight, String) String
        +moveStatus(Flight, FlightStatus)
        +delay(Flight, int)
        +cancel(Flight, String)
        +addFlight(Flight)
        +checkIn(String) Booking
        +execute(AdminCommand, String)
        +undoLast(String) Optional
        +addListener(FlightListener)
    }
    class FlightListener {
        <<interface>>
        +onFlightEvent(FlightEvent)
    }
    class FlightDataSource {
        <<interface>>
        +load() Snapshot
        +save(Snapshot)
    }
    class FlightRepository
    class JsonDataSource
    class AdminCommand {
        <<abstract>>
        +execute(FlightService)*
        +undo(FlightService)*
        +describe()* String
    }
    class FlightCommand {
        <<abstract>>
        #apply(FlightService, Flight)*
    }
    class StatusTransitionStrategy {
        <<interface>>
        +next(Flight, LocalDateTime, Random) Optional
    }
    class Notification {
        <<abstract>>
        +heading()* String
        +body()* String
        +isBroadcast() boolean
        +from(FlightEvent)$ Optional
    }
    class SimulationService
    class FidsController
    FlightService --> FlightDataSource : loads and saves
    FlightDataSource <|.. FlightRepository
    FlightDataSource <|.. JsonDataSource
    FlightService o--> "*" FlightListener : notifies (Observer)
    FlightListener <|.. FidsController
    AdminCommand <|-- FlightCommand
    AdminCommand <|-- AddFlightCommand
    FlightCommand <|-- ChangeGateCommand
    FlightCommand <|-- DelayCommand
    FlightCommand <|-- CancelCommand
    FlightCommand <|-- SetStatusCommand
    FlightService --> AdminCommand : history for undo
    StatusTransitionStrategy <|.. RandomProgressionStrategy
    StatusTransitionStrategy <|.. TimeAwareStrategy
    SimulationService --> StatusTransitionStrategy : uses (Strategy)
    SimulationService --> FlightService
    Notification <|-- GateChangeNotification
    Notification <|-- DelayNotification
    Notification <|-- BoardingCallNotification
    Notification <|-- CancellationNotification
    FidsController --> Notification : builds via factory
```

Patterns used: **Observer** (`FlightListener`), **Strategy** (`StatusTransitionStrategy`, `SortKey` comparators,
`FlightDataSource`), **Command + Memento** (`AdminCommand`, `Flight.Snapshot`), **Template Method**
(`FlightCommand.execute`), **Factory** (`Notification.from`, `Aircraft.of`), **MVC/MVP** (views, `FidsController`,
`AppState`).

## 3. Package diagram

```mermaid
flowchart LR
    ui --> state
    ui --> service
    ui --> notification
    ui --> model
    service --> model
    service --> exception
    notification --> service
    data --> model
    model --> exception
    ui -.->|chooses at start-up| data
```

## 4. Sequence: user searches for a flight

```mermaid
sequenceDiagram
    actor User
    participant LV as LookupView
    participant C as FidsController
    participant S as AppState
    participant FS as FlightService
    User->>LV: types "BK7F3A", presses Enter
    LV->>C: search("BK7F3A")
    C->>S: lookupQuery.set("BK7F3A")
    S-->>C: state changed
    C->>C: refresh()
    C->>FS: lookup("BK7F3A")
    FS-->>C: Flight BA064 (or FlightNotFoundException)
    C->>FS: bookingMatching(...)
    C->>LV: update(flight, booking, ...)
    LV-->>User: "Your Flight" card with countdown
```

## 5. Sequence: simulation changes a gate and the banner appears

```mermaid
sequenceDiagram
    participant T as SimulationService (Timeline)
    participant FS as FlightService
    participant F as Flight
    participant DS as FlightDataSource
    participant C as FidsController
    participant B as BannerView
    T->>FS: changeGate(flight, "B4")
    FS->>FS: ensureGateFree("B4")
    FS->>F: changeGate("B4")
    FS->>DS: save(snapshot)
    FS->>C: onFlightEvent(GATE_CHANGED)
    C->>C: Notification.from(event)
    C->>B: show("GATE CHANGE", ...)
    C->>C: refresh()
```

## 6. State diagram: flight status lifecycle

```mermaid
stateDiagram-v2
    [*] --> OnTime
    OnTime --> Boarding
    OnTime --> Delayed
    Delayed --> Boarding
    Boarding --> GateClosed
    GateClosed --> Departed
    OnTime --> Cancelled
    Delayed --> Cancelled
    Boarding --> Cancelled
    GateClosed --> Cancelled
    Departed --> [*]
    Cancelled --> [*]
```

Staff can also force any status (`SetStatusCommand`), bypassing these rules.
