# Brian — Architecture & OOP

**Time:** about 2 minutes · **Goal:** explain the code structure, then use one file to illustrate OOP.

## Show the architecture

**JavaFX views → FidsController → FlightService → Flight model → FlightDataSource → MySQL**

- **Views** display the board, My Flight, wayfinding, and staff panel.
- **FidsController** routes user actions and refreshes the views.
- **FlightService** applies rules and coordinates saves, events, simulation, and staff actions.
- **Model** holds flight and booking data and protects valid state.
- **FlightDataSource** separates the app from its storage implementation.

**Say:** “Each layer has a clear job. The screens do not contain the flight rules or database code, so a change in one area does not require rewriting the whole app.”

## One short code example: Aircraft.java

Open **src/main/java/com/example/airplane/model/Aircraft.java** and show only these lines:

- **9–13:** Aircraft is abstract; its fields are private. Explain abstraction and encapsulation.
- **29–32:** category and cabinCrew are abstract methods. Each aircraft type provides its own behavior, showing polymorphism.
- **47–56:** the factory creates NarrowBody, RegionalJet, or WideBody objects. These types inherit from Aircraft.

**Say:** “Aircraft keeps shared data private and defines what all aircraft types must provide. The factory creates the right subtype, and that subtype supplies its own category and crew requirement.”

The subclass declarations are in separate files; name the inheritance relationship without opening another file.

## Connect the design to the demo

- **Observer:** flight events tell the controller to refresh the board.
- **Strategy:** staff can switch the simulation between Demo and Realistic behavior.
- **Command + Memento:** staff actions can be undone by restoring a saved flight snapshot.

> “These patterns keep updates, simulation, and undo consistent while the layers keep the code organized. Wendy will briefly show how the data is saved and loaded.”
