package com.example.airplane.ui;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.*;
import com.example.airplane.service.AuditEntry;
import com.example.airplane.service.SimulationService;
import com.example.airplane.service.StatusTransitionStrategy;
import com.example.airplane.service.command.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Staff panel: edit flights, add flights, control the simulation, undo, and read the audit log.
 * OOP: inheritance (extends ScrollPane). Each staff action is wrapped in a Command object
 * (Command design pattern) so it can be validated, logged and undone.
 */
class AdminView extends ScrollPane {
    // Controller that runs staff commands and owns the simulation.
    private final FidsController ctl;
    // Line of text under the title that shows success (green) or error (red) messages.
    private final Label message = Ui.label("", "notice");

    // manage
    // Drop-down listing every flight; staff choose which one to change.
    private final ComboBox<Flight> picker = new ComboBox<>();
    // Input for the new gate.
    private final TextField gateField = new TextField();
    // Input for the delay in minutes.
    private final TextField delayField = new TextField();
    // Input for the cancellation reason.
    private final TextField reasonField = new TextField();
    // Drop-down of all possible statuses (values() returns every enum constant).
    private final ComboBox<FlightStatus> statusBox = new ComboBox<>(FXCollections.observableArrayList(FlightStatus.values()));
    // Button to undo the last staff action.
    private final Button undoBtn = Ui.button("Undo last action", "mdi2a-arrow-down", "action-button");

    // add flight
    // Inputs of the "Add a flight" form.
    private final TextField noField = new TextField();       // flight number
    private final ComboBox<Airline> airlineBox = new ComboBox<>(FXCollections.observableArrayList(SampleData.airlines()));
    private final TextField destField = new TextField();     // destination city
    private final TextField codeField = new TextField();     // destination airport code
    private final TextField timeField = new TextField();     // departure time HH:mm
    private final TextField newGateField = new TextField();  // gate
    private final ComboBox<String> aircraftBox = new ComboBox<>(FXCollections.observableArrayList(Aircraft.knownKeys()));
    private final TextField checkInField = new TextField();  // check-in counters
    private final TextField beltField = new TextField();     // baggage belt

    // simulation + audit
    // Drop-down to choose how flight statuses progress (Strategy pattern: each option is a different algorithm).
    private final ComboBox<StatusTransitionStrategy> strategyBox = new ComboBox<>();
    // On/off button for the simulation.
    private final ToggleButton runToggle = new ToggleButton("Simulation running");
    // Scrollable list of audit log lines.
    private final ListView<String> auditList = new ListView<>();
    // Guard flag: true while update() sets controls by code, so their event handlers ignore those changes.
    private boolean updating;

    /** Constructor: builds the three cards (manage, add, simulation) and wires up button actions. */
    AdminView(FidsController ctl, List<StatusTransitionStrategy> strategies) {
        this.ctl = ctl;
        getStyleClass().add("lookup-scroll");
        setFitToWidth(true);

        // Configure the flight picker: stretch to full width, hint text, and custom cell text.
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.setPromptText("Select a flight…");
        picker.setCellFactory(lv -> flightCell()); // how items look in the opened list
        picker.setButtonCell(flightCell());        // how the selected item looks in the closed box
        picker.getStyleClass().add("flight-picker");
        statusBox.setMaxWidth(Double.MAX_VALUE);
        statusBox.setPromptText("Status…");
        // StringConverter tells the ComboBox how to turn an object into display text.
        // (anonymous class implementing an abstract class; fromString is unused so it returns null)
        statusBox.setConverter(new StringConverter<>() {
            @Override public String toString(FlightStatus s) { return s == null ? "" : s.label(); }
            @Override public FlightStatus fromString(String s) { return null; }
        });
        statusBox.getStyleClass().add("flight-picker");
        gateField.setPromptText("New gate, e.g. B4");
        delayField.setPromptText("Minutes, e.g. 30");
        reasonField.setPromptText("Reason (optional)");

        // Each button builds a Command object for the selected flight and withSelected() runs it.
        Button gateBtn = Ui.button("Change gate", "mdi2d-directions", "action-button");
        gateBtn.setOnAction(e -> withSelected(f -> new ChangeGateCommand(f.id(), gateField.getText().trim().toUpperCase())));
        Button delayBtn = Ui.button("Delay flight", "mdi2c-clock-outline", "action-button");
        delayBtn.setOnAction(e -> withSelected(f -> {
            try {
                return new DelayCommand(f.id(), Integer.parseInt(delayField.getText().trim())); // text -> int
            } catch (NumberFormatException ex) {
                // The user typed something that is not a whole number.
                show("Enter the delay as a whole number of minutes.", false);
                return null; // null means "no command to run"
            }
        }));
        Button cancelBtn = Ui.button("Cancel flight", "mdi2c-close-circle", "action-button");
        cancelBtn.setOnAction(e -> withSelected(f -> new CancelCommand(f.id(), reasonField.getText())));
        Button statusBtn = Ui.button("Force status", "mdi2a-airplane", "action-button");
        statusBtn.setOnAction(e -> withSelected(f -> {
            if (statusBox.getValue() == null) { show("Choose a status first.", false); return null; }
            return new SetStatusCommand(f.id(), statusBox.getValue());
        }));
        undoBtn.setOnAction(e -> {
            Optional<String> err = ctl.undoStaff(); // empty Optional = success, otherwise it holds the error text
            show(err.orElse("Last action undone."), err.isEmpty());
        });

        // First card: the picker followed by one row per action (input + button).
        VBox manage = card("Manage a flight", picker,
                row(gateField, gateBtn), row(delayField, delayBtn), row(reasonField, cancelBtn),
                row(statusBox, statusBtn), undoBtn);

        // add flight form
        noField.setPromptText("Flight no., e.g. KQ777");
        airlineBox.setPromptText("Airline");
        airlineBox.setMaxWidth(Double.MAX_VALUE);
        airlineBox.getStyleClass().add("flight-picker");
        // Show an airline as "CODE · Name".
        airlineBox.setConverter(new StringConverter<>() {
            @Override public String toString(Airline a) { return a == null ? "" : a.code() + " · " + a.name(); }
            @Override public Airline fromString(String s) { return null; }
        });
        destField.setPromptText("Destination, e.g. Cairo");
        codeField.setPromptText("Code, e.g. CAI");
        timeField.setPromptText("Time HH:mm, e.g. 18:45");
        newGateField.setPromptText("Gate, e.g. C4");
        aircraftBox.setPromptText("Aircraft");
        aircraftBox.setValue("A320"); // default aircraft type
        aircraftBox.setMaxWidth(Double.MAX_VALUE);
        aircraftBox.getStyleClass().add("flight-picker");
        checkInField.setPromptText("Check-in, e.g. D 10-14");
        beltField.setPromptText("Belt, e.g. Belt 2");
        Button addBtn = Ui.button("Add flight", "mdi2a-airplane-takeoff", "action-button", "primary");
        addBtn.setOnAction(e -> addFlight());
        // Second card: all form fields in order, then the Add button.
        VBox add = card("Add a flight", noField, airlineBox, destField, codeField, timeField, newGateField,
                aircraftBox, checkInField, beltField, addBtn);

        // simulation + audit
        strategyBox.setItems(FXCollections.observableArrayList(strategies)); // fill the drop-down with the strategies given
        strategyBox.setMaxWidth(Double.MAX_VALUE);
        strategyBox.getStyleClass().add("flight-picker");
        strategyBox.setConverter(new StringConverter<>() {
            @Override public String toString(StatusTransitionStrategy s) { return s == null ? "" : s.name(); }
            @Override public StatusTransitionStrategy fromString(String s) { return null; }
        });
        // When the user picks a strategy, give it to the simulation (ignored if update() changed it by code).
        strategyBox.setOnAction(e -> {
            if (!updating && strategyBox.getValue() != null) ctl.simulation().setStrategy(strategyBox.getValue());
        });
        runToggle.setFocusTraversable(false);
        // Start/pause the simulation and update the button text.
        runToggle.setOnAction(e -> {
            if (!updating) ctl.simulation().setRunning(runToggle.isSelected());
            runToggle.setText(runToggle.isSelected() ? "Simulation running" : "Simulation paused");
        });
        Button triggerBtn = Ui.button("Trigger a gate change now", "mdi2a-alert", "action-button");
        // triggerGateChange() returns true if a flight changed gate; the ternary picks the matching message.
        triggerBtn.setOnAction(e -> show(ctl.simulation().triggerGateChange()
                ? "A random gate change was triggered." : "No flight could change gate right now.", true));
        auditList.getStyleClass().add("audit-list");
        auditList.setPrefHeight(300);
        auditList.setPlaceholder(new Label("No changes yet"));
        // Third card: strategy chooser, run toggle, trigger button, and the audit log.
        VBox sim = card("Simulation and audit log", Ui.label("PROGRESSION STRATEGY", "detail-caption"), strategyBox,
                runToggle, triggerBtn, Ui.label("AUDIT LOG (newest first)", "detail-caption"), auditList);

        // Put the three cards side by side and let each grow equally.
        HBox columns = new HBox(14, manage, add, sim);
        columns.setAlignment(Pos.TOP_LEFT);
        for (Node n : columns.getChildren()) { HBox.setHgrow(n, Priority.ALWAYS); ((Region) n).setPrefWidth(300); }
        // Whole page: heading, message line, then the three cards.
        VBox content = new VBox(14, new VBox(2, Ui.label("Staff panel", "board-title"),
                Ui.label("Changes are validated, logged, saved and announced on the board", "board-sub")),
                message, columns);
        content.setPadding(new Insets(16, 18, 18, 18));
        setContent(content);
    }

    /** Creates a cell that shows a flight as "number · destination · gate · status". */
    private static ListCell<Flight> flightCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Flight f, boolean empty) {
                super.updateItem(f, empty);
                setText(empty || f == null ? null : f.displayNumber() + "  ·  " + f.destination()
                        + "  ·  " + f.gate() + "  ·  " + f.status().label());
            }
        };
    }

    /** Helper: makes a white card with a title and the given children (varargs = any number of nodes). */
    private VBox card(String title, Node... children) {
        VBox box = new VBox(10, Ui.label(title, "detail-title"));
        box.getChildren().addAll(children);
        box.getStyleClass().add("admin-card");
        for (Node n : children) {
            // "instanceof TextField tf" checks the type and gives a typed variable in one step (pattern matching).
            if (n instanceof TextField tf) tf.getStyleClass().add("form-field");
        }
        return box;
    }

    /** Helper: one horizontal row with an input on the left (stretching) and a button on the right. */
    private HBox row(Node field, Button button) {
        if (field instanceof TextField tf) tf.getStyleClass().add("form-field");
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox h = new HBox(8, field, button);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ---- actions -----------------------------------------------------------------------------

    /**
     * Runs a staff action on the flight chosen in the picker.
     * The factory is a lambda that builds the right AdminCommand (polymorphism: all commands share the AdminCommand type).
     */
    private void withSelected(java.util.function.Function<Flight, AdminCommand> factory) {
        Flight f = picker.getValue();
        if (f == null) { show("Select a flight first.", false); return; }
        AdminCommand cmd = factory.apply(f); // build the command for this flight
        if (cmd == null) return; // the factory already showed an error
        Optional<String> err = ctl.runStaff(cmd); // controller validates, runs, logs and saves it
        show(err.orElse("Done: " + cmd.describe()), err.isEmpty());
    }

    /** Reads the "Add a flight" form, builds a new Flight and asks the controller to add it. */
    private void addFlight() {
        try {
            if (airlineBox.getValue() == null) { show("Choose an airline.", false); return; }
            LocalTime time = LocalTime.parse(timeField.getText().trim()); // throws DateTimeParseException if badly typed
            LocalDateTime when = LocalDate.now().atTime(time); // today at that time
            if (when.isBefore(LocalDateTime.now())) when = when.plusDays(1); // time already passed, so use tomorrow
            String no = noField.getText().replaceAll("\\s+", "").toUpperCase(); // remove spaces, make capitals
            // Create the Flight object; origin is fixed to Nairobi and a new flight starts ON_TIME.
            Flight f = new Flight(no, airlineBox.getValue(), "Nairobi", destField.getText(), codeField.getText(),
                    when, newGateField.getText().trim().toUpperCase(), FlightStatus.ON_TIME,
                    orTba(checkInField.getText()), orTba(beltField.getText()), Aircraft.of(aircraftBox.getValue()));
            AdminCommand cmd = new AddFlightCommand(f);
            Optional<String> err = ctl.runStaff(cmd);
            show(err.orElse("Done: " + cmd.describe()), err.isEmpty());
            if (err.isEmpty()) {
                // Success: clear the text boxes ready for the next flight.
                for (TextField t : List.of(noField, destField, codeField, timeField, newGateField, checkInField, beltField)) t.clear();
            }
        } catch (DateTimeParseException e) {
            show("Time must look like 18:45 (24-hour HH:mm).", false);
        } catch (FidsException e) {
            // Our own exception class: thrown when the flight data fails validation.
            show(e.getMessage(), false);
        }
    }

    /** Returns "TBA" (to be announced) when the text is empty, otherwise the trimmed text. */
    private static String orTba(String s) { return s == null || s.isBlank() ? "TBA" : s.trim(); }

    /** Shows a message in green (ok = true) or red (ok = false) by swapping the CSS class. */
    private void show(String text, boolean ok) {
        message.setText(text);
        message.getStyleClass().removeAll("notice", "notice-error");
        message.getStyleClass().add(ok ? "notice" : "notice-error");
    }

    // ---- refresh -------------------------------------------------------------------------------

    /** Called by the controller to refresh the flight list, audit log, undo button and simulation controls. */
    void update(List<Flight> flights, List<AuditEntry> audit, boolean canUndo, SimulationService sim) {
        updating = true; // tell the handlers to ignore the changes made below
        Flight selected = picker.getValue();
        // Only reload the picker if the flight list changed, then try to keep the same flight selected.
        if (!new ArrayList<>(picker.getItems()).equals(flights)) {
            picker.getItems().setAll(flights);
            if (selected != null && flights.contains(selected)) picker.setValue(selected);
        }
        // Re-render the selected item's text (status/gate may have changed).
        picker.setButtonCell(flightCell());
        // Convert each AuditEntry to text with a stream and show newest first (as supplied).
        auditList.getItems().setAll(audit.stream().map(AuditEntry::toString).toList());
        undoBtn.setDisable(!canUndo); // disabled when there is nothing to undo
        strategyBox.setValue(sim.strategy());
        runToggle.setSelected(sim.isRunning());
        runToggle.setText(sim.isRunning() ? "Simulation running" : "Simulation paused");
        updating = false;
    }
}
