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
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    // Drop-down of the gates that are free for the selected flight (so staff never have to guess).
    private final ComboBox<String> gateBox = new ComboBox<>();
    // Drop-down of common delay lengths, in minutes.
    private final ComboBox<Integer> delayBox = new ComboBox<>(FXCollections.observableArrayList(
            10, 15, 20, 30, 45, 60, 90, 120, 180, 240));
    // Editable drop-down: pick a common reason for the delay or type your own (a reason is required).
    private final ComboBox<String> delayReasonBox = new ComboBox<>(FXCollections.observableArrayList(
            "Late arrival of inbound aircraft.", "Bad weather", "Technical check", "Crew delay",
            "Air traffic control restrictions", "Baggage loading delay"));
    // Editable drop-down: pick a common cancellation reason or type your own.
    private final ComboBox<String> reasonBox = new ComboBox<>(FXCollections.observableArrayList(
            "Bad weather", "Technical fault", "Aircraft unavailable.", "Crew unavailable",
            "Air traffic control restrictions", "Operational reasons."));
    // Drop-down of all possible statuses (values() returns every enum constant).
    private final ComboBox<FlightStatus> statusBox = new ComboBox<>(FXCollections.observableArrayList(FlightStatus.values()));
    // Button to undo the last staff action.
    private final Button undoBtn = Ui.button("Undo last action", "mdi2a-arrow-down", "action-button");

    // add flight
    // Inputs of the "Add a flight" form.
    private final TextField noField = new TextField();       // flight number digits (the airline code is added for you)
    private final ComboBox<Airline> airlineBox = new ComboBox<>(FXCollections.observableArrayList(SampleData.airlines()));
    // Destination drop-down; choosing a city also fills in its airport code.
    private final ComboBox<SampleData.Destination> destBox = new ComboBox<>(FXCollections.observableArrayList(SampleData.destinations()));
    // Departure time as two drop-downs: hour (00-23) and minute (every 5 minutes).
    private final ComboBox<String> hourBox = new ComboBox<>(FXCollections.observableArrayList(numbers(24, 1)));
    private final ComboBox<String> minuteBox = new ComboBox<>(FXCollections.observableArrayList(numbers(60, 5)));
    // Only the gates that are free around the chosen time are listed here.
    private final ComboBox<String> newGateBox = new ComboBox<>();
    private final ComboBox<String> aircraftBox = new ComboBox<>(FXCollections.observableArrayList(Aircraft.knownKeys()));
    private final ComboBox<String> checkInBox = new ComboBox<>(FXCollections.observableArrayList(withTba(SampleData.checkInDesks())));
    private final ComboBox<String> beltBox = new ComboBox<>(FXCollections.observableArrayList(withTba(SampleData.baggageBelts())));

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
        // Whenever another flight is chosen, list only the gates that are free for it.
        picker.valueProperty().addListener((o, a, b) -> refreshGateChoices());
        dropDown(gateBox, "Select a flight first");
        dropDown(delayBox, "Delay length");
        delayBox.setConverter(new StringConverter<>() {
            @Override public String toString(Integer m) { return m == null ? "" : m + " minutes"; }
            @Override public Integer fromString(String s) { return null; }
        });
        dropDown(delayReasonBox, "Reason for delay (required)");
        delayReasonBox.setEditable(true);
        dropDown(reasonBox, "Reason for cancellation (pick or type)");
        reasonBox.setEditable(true); // allow a custom reason as well as the presets

        // Each button builds a Command object for the selected flight and withSelected() runs it.
        Button gateBtn = Ui.button("Change gate", "mdi2d-directions", "action-button");
        gateBtn.setOnAction(e -> withSelected(f -> {
            if (gateBox.getValue() == null) { show("Choose a gate first.", false); return null; }
            return new ChangeGateCommand(f.id(), gateBox.getValue());
        }));
        Button delayBtn = Ui.button("Delay flight", "mdi2c-clock-outline", "action-button");
        delayBtn.setOnAction(e -> withSelected(f -> {
            if (delayBox.getValue() == null) { show("Choose how long the delay is.", false); return null; }
            String why = delayReasonBox.getEditor().getText().trim();
            if (why.isEmpty()) { show("Give a reason for the delay.", false); return null; }
            return new DelayCommand(f.id(), delayBox.getValue(), why); // null from the factory means "no command to run"
        }));
        Button cancelBtn = Ui.button("Cancel flight", "mdi2c-close-circle", "action-button");
        cancelBtn.setOnAction(e -> withSelected(f -> new CancelCommand(f.id(), reasonBox.getEditor().getText())));
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
                row(gateBox, gateBtn), delayReasonBox, row(delayBox, delayBtn), row(reasonBox, cancelBtn),
                row(statusBox, statusBtn), undoBtn);

        // add flight form
        noField.setPromptText("Flight number digits, e.g. 777");
        // Only digits, at most 4 (the TextFormatter rejects any other key press).
        noField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,4}") ? c : null));
        dropDown(airlineBox, "Airline");
        // Show an airline as "CODE · Name".
        airlineBox.setConverter(new StringConverter<>() {
            @Override public String toString(Airline a) { return a == null ? "" : a.code() + " · " + a.name(); }
            @Override public Airline fromString(String s) { return null; }
        });
        dropDown(destBox, "Destination");
        destBox.setConverter(new StringConverter<>() {
            @Override public String toString(SampleData.Destination d) { return d == null ? "" : d.city() + " (" + d.code() + ")"; }
            @Override public SampleData.Destination fromString(String s) { return null; }
        });
        dropDown(hourBox, "Hour");
        dropDown(minuteBox, "Min");
        // Gates depend on the departure time, so refresh them whenever the hour or minute changes.
        hourBox.valueProperty().addListener((o, a, b) -> refreshNewGateChoices());
        minuteBox.valueProperty().addListener((o, a, b) -> refreshNewGateChoices());
        HBox timeRow = new HBox(8, hourBox, Ui.label(":", "detail-title"), minuteBox);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        dropDown(newGateBox, "Pick a time to see free gates");
        dropDown(aircraftBox, "Aircraft");
        aircraftBox.setValue("A320"); // default aircraft type
        dropDown(checkInBox, "Check-in desks");
        checkInBox.setValue("TBA");
        dropDown(beltBox, "Baggage belt");
        beltBox.setValue("TBA");
        Button addBtn = Ui.button("Add flight", "mdi2a-airplane-takeoff", "action-button", "primary");
        addBtn.setOnAction(e -> addFlight());
        // Second card: all form fields in order, then the Add button.
        // Every field gets a small caption above it, so it is clear what each box is for even after it has a value.
        VBox add = card("Add a flight", captioned("AIRLINE", airlineBox), captioned("FLIGHT NUMBER (digits only)", noField),
                captioned("DESTINATION", destBox), captioned("DEPARTURE TIME (24-hour)", timeRow),
                captioned("GATE (only free gates are listed)", newGateBox), captioned("AIRCRAFT TYPE", aircraftBox),
                captioned("CHECK-IN DESKS", checkInBox), captioned("BAGGAGE BELT", beltBox), addBtn);

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
            // Every choice must be made before a flight can be built.
            if (airlineBox.getValue() == null) { show("Choose an airline.", false); return; }
            if (noField.getText().isBlank()) { show("Enter the flight number digits.", false); return; }
            if (destBox.getValue() == null) { show("Choose a destination.", false); return; }
            LocalDateTime when = departureTime();
            if (when == null) { show("Choose the departure hour and minute.", false); return; }
            if (newGateBox.getValue() == null) { show("Choose a gate.", false); return; }
            SampleData.Destination dest = destBox.getValue();
            String no = airlineBox.getValue().code() + noField.getText().trim(); // airline code + digits, e.g. KQ777
            // Create the Flight object; origin is fixed to Nairobi and a new flight starts ON_TIME.
            Flight f = new Flight(no, airlineBox.getValue(), "Nairobi", dest.city(), dest.code(),
                    when, newGateBox.getValue(), FlightStatus.ON_TIME,
                    checkInBox.getValue(), beltBox.getValue(), Aircraft.of(aircraftBox.getValue()));
            AdminCommand cmd = new AddFlightCommand(f);
            Optional<String> err = ctl.runStaff(cmd);
            show(err.orElse("Done: " + cmd.describe()), err.isEmpty());
            if (err.isEmpty()) {
                // Success: clear the form ready for the next flight.
                noField.clear();
                destBox.setValue(null);
                hourBox.setValue(null);
                minuteBox.setValue(null);
                checkInBox.setValue("TBA");
                beltBox.setValue("TBA");
            }
        } catch (FidsException e) {
            // Our own exception class: thrown when the flight data fails validation.
            show(e.getMessage(), false);
        }
    }

    /** Helper: puts a small grey caption above a form field (and gives text boxes the form style first). */
    private static VBox captioned(String caption, Node field) {
        if (field instanceof TextField tf) tf.getStyleClass().add("form-field");
        return new VBox(3, Ui.label(caption, "detail-caption"), field);
    }

    /** Helper: styles a drop-down like the others (full width, classic look) and sets its hint text. */
    private static void dropDown(ComboBox<?> box, String prompt) {
        box.setPromptText(prompt);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("flight-picker");
    }

    /** Helper: the numbers 0 .. limit-1 in steps, as two-digit text ("00", "05", ...). */
    private static List<String> numbers(int limit, int step) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < limit; i += step) out.add(String.format("%02d", i));
        return out;
    }

    /** Helper: the given options with "TBA" (to be announced) added at the front. */
    private static List<String> withTba(List<String> options) {
        List<String> out = new ArrayList<>();
        out.add("TBA");
        out.addAll(options);
        return out;
    }

    /** The departure time chosen in the form (today, or tomorrow if that time has passed); null if not chosen yet. */
    private LocalDateTime departureTime() {
        if (hourBox.getValue() == null || minuteBox.getValue() == null) return null;
        LocalDateTime when = LocalDate.now().atTime(Integer.parseInt(hourBox.getValue()), Integer.parseInt(minuteBox.getValue()));
        return when.isBefore(LocalDateTime.now()) ? when.plusDays(1) : when;
    }

    /** Helper: replaces a drop-down's options but keeps the current choice when it is still valid. */
    private static void setChoices(ComboBox<String> box, List<String> items) {
        if (box.getItems().equals(items)) return; // nothing changed: don't disturb an open list
        String keep = box.getValue();
        box.getItems().setAll(items);
        box.setValue(keep != null && items.contains(keep) ? keep : null);
    }

    /** "Change gate" drop-down: gates that are free at the selected flight's time (not counting its own gate). */
    private void refreshGateChoices() {
        Flight f = picker.getValue();
        if (f == null) { setChoices(gateBox, List.of()); gateBox.setPromptText("Select a flight first"); return; }
        setChoices(gateBox, ctl.freeGates(f.scheduledTime(), f).stream().filter(g -> !g.equals(f.gate())).toList());
        gateBox.setPromptText("New gate (" + gateBox.getItems().size() + " free)");
    }

    /** "Add flight" gate drop-down: gates that are free around the chosen departure time. */
    private void refreshNewGateChoices() {
        LocalDateTime when = departureTime();
        if (when == null) { setChoices(newGateBox, List.of()); newGateBox.setPromptText("Pick a time to see free gates"); return; }
        setChoices(newGateBox, ctl.freeGates(when, null));
        newGateBox.setPromptText("Gate (" + newGateBox.getItems().size() + " free)");
    }

    /** Shows a message in green (ok = true) or red (ok = false) by swapping the CSS class. */
    private void show(String text, boolean ok) {
        message.setText(text);
        setVvalue(0); // scroll to the top, where the message line is
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
        // Flights changed, so the lists of free gates may have changed too.
        refreshGateChoices();
        refreshNewGateChoices();
        // Convert each AuditEntry to text with a stream and show newest first (as supplied).
        auditList.getItems().setAll(audit.stream().map(AuditEntry::toString).toList());
        undoBtn.setDisable(!canUndo); // disabled when there is nothing to undo
        strategyBox.setValue(sim.strategy());
        runToggle.setSelected(sim.isRunning());
        runToggle.setText(sim.isRunning() ? "Simulation running" : "Simulation paused");
        updating = false;
    }
}
