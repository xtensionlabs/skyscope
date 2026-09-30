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

/** Staff panel: edit flights, add flights, control the simulation, undo, and read the audit log. */
class AdminView extends ScrollPane {
    private final FidsController ctl;
    private final Label message = Ui.label("", "notice");

    // manage
    private final ComboBox<Flight> picker = new ComboBox<>();
    private final TextField gateField = new TextField();
    private final TextField delayField = new TextField();
    private final TextField reasonField = new TextField();
    private final ComboBox<FlightStatus> statusBox = new ComboBox<>(FXCollections.observableArrayList(FlightStatus.values()));
    private final Button undoBtn = Ui.button("Undo last action", "mdi2a-arrow-down", "action-button");

    // add flight
    private final TextField noField = new TextField();
    private final ComboBox<Airline> airlineBox = new ComboBox<>(FXCollections.observableArrayList(SampleData.airlines()));
    private final TextField destField = new TextField();
    private final TextField codeField = new TextField();
    private final TextField timeField = new TextField();
    private final TextField newGateField = new TextField();
    private final ComboBox<String> aircraftBox = new ComboBox<>(FXCollections.observableArrayList(Aircraft.knownKeys()));
    private final TextField checkInField = new TextField();
    private final TextField beltField = new TextField();

    // simulation + audit
    private final ComboBox<StatusTransitionStrategy> strategyBox = new ComboBox<>();
    private final ToggleButton runToggle = new ToggleButton("Simulation running");
    private final ListView<String> auditList = new ListView<>();
    private boolean updating;

    AdminView(FidsController ctl, List<StatusTransitionStrategy> strategies) {
        this.ctl = ctl;
        getStyleClass().add("lookup-scroll");
        setFitToWidth(true);

        picker.setMaxWidth(Double.MAX_VALUE);
        picker.setPromptText("Select a flight…");
        picker.setCellFactory(lv -> flightCell());
        picker.setButtonCell(flightCell());
        picker.getStyleClass().add("flight-picker");
        statusBox.setMaxWidth(Double.MAX_VALUE);
        statusBox.setPromptText("Status…");
        statusBox.setConverter(new StringConverter<>() {
            @Override public String toString(FlightStatus s) { return s == null ? "" : s.label(); }
            @Override public FlightStatus fromString(String s) { return null; }
        });
        statusBox.getStyleClass().add("flight-picker");
        gateField.setPromptText("New gate, e.g. B4");
        delayField.setPromptText("Minutes, e.g. 30");
        reasonField.setPromptText("Reason (optional)");

        Button gateBtn = Ui.button("Change gate", "mdi2d-directions", "action-button");
        gateBtn.setOnAction(e -> withSelected(f -> new ChangeGateCommand(f.id(), gateField.getText().trim().toUpperCase())));
        Button delayBtn = Ui.button("Delay flight", "mdi2c-clock-outline", "action-button");
        delayBtn.setOnAction(e -> withSelected(f -> {
            try {
                return new DelayCommand(f.id(), Integer.parseInt(delayField.getText().trim()));
            } catch (NumberFormatException ex) {
                show("Enter the delay as a whole number of minutes.", false);
                return null;
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
            Optional<String> err = ctl.undoStaff();
            show(err.orElse("Last action undone."), err.isEmpty());
        });

        VBox manage = card("Manage a flight", picker,
                row(gateField, gateBtn), row(delayField, delayBtn), row(reasonField, cancelBtn),
                row(statusBox, statusBtn), undoBtn);

        // add flight form
        noField.setPromptText("Flight no., e.g. KQ777");
        airlineBox.setPromptText("Airline");
        airlineBox.setMaxWidth(Double.MAX_VALUE);
        airlineBox.getStyleClass().add("flight-picker");
        airlineBox.setConverter(new StringConverter<>() {
            @Override public String toString(Airline a) { return a == null ? "" : a.code() + " · " + a.name(); }
            @Override public Airline fromString(String s) { return null; }
        });
        destField.setPromptText("Destination, e.g. Cairo");
        codeField.setPromptText("Code, e.g. CAI");
        timeField.setPromptText("Time HH:mm, e.g. 18:45");
        newGateField.setPromptText("Gate, e.g. C4");
        aircraftBox.setPromptText("Aircraft");
        aircraftBox.setValue("A320");
        aircraftBox.setMaxWidth(Double.MAX_VALUE);
        aircraftBox.getStyleClass().add("flight-picker");
        checkInField.setPromptText("Check-in, e.g. D 10-14");
        beltField.setPromptText("Belt, e.g. Belt 2");
        Button addBtn = Ui.button("Add flight", "mdi2a-airplane-takeoff", "action-button", "primary");
        addBtn.setOnAction(e -> addFlight());
        VBox add = card("Add a flight", noField, airlineBox, destField, codeField, timeField, newGateField,
                aircraftBox, checkInField, beltField, addBtn);

        // simulation + audit
        strategyBox.setItems(FXCollections.observableArrayList(strategies));
        strategyBox.setMaxWidth(Double.MAX_VALUE);
        strategyBox.getStyleClass().add("flight-picker");
        strategyBox.setConverter(new StringConverter<>() {
            @Override public String toString(StatusTransitionStrategy s) { return s == null ? "" : s.name(); }
            @Override public StatusTransitionStrategy fromString(String s) { return null; }
        });
        strategyBox.setOnAction(e -> {
            if (!updating && strategyBox.getValue() != null) ctl.simulation().setStrategy(strategyBox.getValue());
        });
        runToggle.setFocusTraversable(false);
        runToggle.setOnAction(e -> {
            if (!updating) ctl.simulation().setRunning(runToggle.isSelected());
            runToggle.setText(runToggle.isSelected() ? "Simulation running" : "Simulation paused");
        });
        Button triggerBtn = Ui.button("Trigger a gate change now", "mdi2a-alert", "action-button");
        triggerBtn.setOnAction(e -> show(ctl.simulation().triggerGateChange()
                ? "A random gate change was triggered." : "No flight could change gate right now.", true));
        auditList.getStyleClass().add("audit-list");
        auditList.setPrefHeight(300);
        auditList.setPlaceholder(new Label("No changes yet"));
        VBox sim = card("Simulation and audit log", Ui.label("PROGRESSION STRATEGY", "detail-caption"), strategyBox,
                runToggle, triggerBtn, Ui.label("AUDIT LOG (newest first)", "detail-caption"), auditList);

        HBox columns = new HBox(14, manage, add, sim);
        columns.setAlignment(Pos.TOP_LEFT);
        for (Node n : columns.getChildren()) { HBox.setHgrow(n, Priority.ALWAYS); ((Region) n).setPrefWidth(300); }
        VBox content = new VBox(14, new VBox(2, Ui.label("Staff panel", "board-title"),
                Ui.label("Changes are validated, logged, saved and announced on the board", "board-sub")),
                message, columns);
        content.setPadding(new Insets(16, 18, 18, 18));
        setContent(content);
    }

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

    private VBox card(String title, Node... children) {
        VBox box = new VBox(10, Ui.label(title, "detail-title"));
        box.getChildren().addAll(children);
        box.getStyleClass().add("admin-card");
        for (Node n : children) {
            if (n instanceof TextField tf) tf.getStyleClass().add("form-field");
        }
        return box;
    }

    private HBox row(Node field, Button button) {
        if (field instanceof TextField tf) tf.getStyleClass().add("form-field");
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox h = new HBox(8, field, button);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ---- actions -----------------------------------------------------------------------------

    private void withSelected(java.util.function.Function<Flight, AdminCommand> factory) {
        Flight f = picker.getValue();
        if (f == null) { show("Select a flight first.", false); return; }
        AdminCommand cmd = factory.apply(f);
        if (cmd == null) return;
        Optional<String> err = ctl.runStaff(cmd);
        show(err.orElse("Done: " + cmd.describe()), err.isEmpty());
    }

    private void addFlight() {
        try {
            if (airlineBox.getValue() == null) { show("Choose an airline.", false); return; }
            LocalTime time = LocalTime.parse(timeField.getText().trim());
            LocalDateTime when = LocalDate.now().atTime(time);
            if (when.isBefore(LocalDateTime.now())) when = when.plusDays(1);
            String no = noField.getText().replaceAll("\\s+", "").toUpperCase();
            Flight f = new Flight(no, airlineBox.getValue(), "Nairobi", destField.getText(), codeField.getText(),
                    when, newGateField.getText().trim().toUpperCase(), FlightStatus.ON_TIME,
                    orTba(checkInField.getText()), orTba(beltField.getText()), Aircraft.of(aircraftBox.getValue()));
            AdminCommand cmd = new AddFlightCommand(f);
            Optional<String> err = ctl.runStaff(cmd);
            show(err.orElse("Done: " + cmd.describe()), err.isEmpty());
            if (err.isEmpty()) {
                for (TextField t : List.of(noField, destField, codeField, timeField, newGateField, checkInField, beltField)) t.clear();
            }
        } catch (DateTimeParseException e) {
            show("Time must look like 18:45 (24-hour HH:mm).", false);
        } catch (FidsException e) {
            show(e.getMessage(), false);
        }
    }

    private static String orTba(String s) { return s == null || s.isBlank() ? "TBA" : s.trim(); }

    private void show(String text, boolean ok) {
        message.setText(text);
        message.getStyleClass().removeAll("notice", "notice-error");
        message.getStyleClass().add(ok ? "notice" : "notice-error");
    }

    // ---- refresh -------------------------------------------------------------------------------

    void update(List<Flight> flights, List<AuditEntry> audit, boolean canUndo, SimulationService sim) {
        updating = true;
        Flight selected = picker.getValue();
        if (!new ArrayList<>(picker.getItems()).equals(flights)) {
            picker.getItems().setAll(flights);
            if (selected != null && flights.contains(selected)) picker.setValue(selected);
        }
        // Re-render the selected item's text (status/gate may have changed).
        picker.setButtonCell(flightCell());
        auditList.getItems().setAll(audit.stream().map(AuditEntry::toString).toList());
        undoBtn.setDisable(!canUndo);
        strategyBox.setValue(sim.strategy());
        runToggle.setSelected(sim.isRunning());
        runToggle.setText(sim.isRunning() ? "Simulation running" : "Simulation paused");
        updating = false;
    }
}
