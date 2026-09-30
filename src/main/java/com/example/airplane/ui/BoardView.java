package com.example.airplane.ui;

import com.example.airplane.model.Flight;
import com.example.airplane.state.AppState;
import com.example.airplane.state.AppState.SortKey;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The main departures board: a dense FIDS-style table plus an animated detail drawer. */
class BoardView extends VBox {
    private static final double DETAIL_H = 112;

    private final FidsController ctl;
    private final TableView<Flight> table = new TableView<>();
    private final Label subtitle = Ui.label("", "board-sub");
    private final Label updated = Ui.label("", "board-sub");
    private final Circle liveDot = new Circle(5);
    private final HBox detailContent = new HBox(32);
    private final StackPane detailWrap = new StackPane(detailContent);
    private final Map<SortKey, FontIcon> arrows = new EnumMap<>(SortKey.class);
    private final Map<SortKey, HBox> headers = new EnumMap<>(SortKey.class);
    private AppState state;
    private String flashId;
    private boolean detailOpen;
    private Timeline detailAnim;
    private LocalDateTime lastShownUpdate;

    BoardView(FidsController ctl) {
        super(0);
        this.ctl = ctl;
        getStyleClass().add("board");
        setPadding(new Insets(14, 18, 16, 18));

        VBox titles = new VBox(2, Ui.label("Departures", "board-title"), subtitle);
        liveDot.getStyleClass().add("live-dot");
        HBox live = new HBox(6, liveDot, Ui.label("LIVE", "live-text"), updated);
        live.setAlignment(Pos.CENTER_RIGHT);
        live.setAccessibleText("Live board");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox top = new HBox(titles, grow, live);
        top.setAlignment(Pos.BOTTOM_LEFT);
        top.setPadding(new Insets(0, 0, 12, 0));

        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        detailContent.getStyleClass().add("detail-content");
        detailContent.setAlignment(Pos.CENTER_LEFT);
        detailContent.setMinHeight(DETAIL_H);
        detailContent.setMaxHeight(DETAIL_H);
        detailWrap.setAlignment(Pos.TOP_LEFT);
        detailWrap.setPrefHeight(0);
        detailWrap.setMinHeight(0);
        detailWrap.setMaxHeight(0);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(detailWrap.widthProperty());
        clip.heightProperty().bind(detailWrap.heightProperty());
        detailWrap.setClip(clip);
        detailWrap.getStyleClass().add("detail-wrap");

        getChildren().addAll(top, table, detailWrap);
    }

    private void buildTable() {
        table.getStyleClass().add("fids-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(44);
        table.setPlaceholder(new Label("No flights to display"));
        table.setAccessibleText("Departures board. Use the arrow keys to move, Enter to expand, P to pin, R for route.");

        table.getColumns().add(column("", 44, null, f -> pinCell(f)));
        table.getColumns().add(column("FLIGHT", 100, null, f -> Ui.label(f.displayNumber(), "cell-flight")));
        table.getColumns().add(column("AIRLINE", 200, null, f -> {
            HBox b = new HBox(10, Ui.airlineChip(f.airline()), Ui.label(f.airline().name(), "cell-text"));
            b.setAlignment(Pos.CENTER_LEFT);
            return b;
        }));
        table.getColumns().add(column("DESTINATION", 220, null, f -> {
            HBox b = new HBox(10, Ui.label(f.destination().toUpperCase(), "cell-dest"),
                    Ui.label(f.destinationCode(), "cell-code"));
            b.setAlignment(Pos.CENTER_LEFT);
            return b;
        }));
        table.getColumns().add(column("TIME", 130, SortKey.TIME, f -> {
            HBox b = new HBox(8, Ui.label(Ui.HHMM.format(f.scheduledTime()), "cell-time"));
            if (!f.estimatedTime().equals(f.scheduledTime())) {
                b.getChildren().add(Ui.label("→ " + Ui.HHMM.format(f.estimatedTime()), "cell-est"));
            }
            b.setAlignment(Pos.CENTER_LEFT);
            return b;
        }));
        table.getColumns().add(column("GATE", 80, SortKey.GATE, f -> {
            Label gate = Ui.label(f.gate(), "cell-gate");
            gate.setTooltip(new Tooltip(f.terminal() + " · Pier " + f.pier()));
            return gate;
        }));
        table.getColumns().add(column("STATUS", 170, SortKey.STATUS, f -> {
            Node badge = Ui.statusBadge(f.status());
            Tooltip.install(badge, new Tooltip(f.status().announcement(f)));
            if (f.id().equals(flashId)) flash(badge);
            return badge;
        }));

        table.setRowFactory(tv -> {
            TableRow<Flight> row = new TableRow<>() {
                @Override
                protected void updateItem(Flight f, boolean empty) {
                    super.updateItem(f, empty);
                    getStyleClass().removeAll("pinned-row", "expanded-row", "inactive-row");
                    if (f == null || empty || state == null) {
                        setAccessibleText(null);
                        return;
                    }
                    if (f.id().equals(state.pinnedFlightId.get())) getStyleClass().add("pinned-row");
                    if (f.id().equals(state.expandedFlightId.get())) getStyleClass().add("expanded-row");
                    if (!f.status().isActive()) getStyleClass().add("inactive-row");
                    setAccessibleText(f.displayNumber() + " to " + f.destination() + ", departs "
                            + Ui.HHMM.format(f.scheduledTime()) + ", gate " + f.gate() + ", " + f.status().label());
                }
            };
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) ctl.onRowClicked(row.getItem().id());
            });
            return row;
        });

        // Keyboard: arrows move (built in), Enter/Space expand, P pins, R shows the route.
        table.setOnKeyPressed(e -> {
            Flight f = table.getSelectionModel().getSelectedItem();
            if (f == null) return;
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) { ctl.onRowClicked(f.id()); e.consume(); }
            else if (e.getCode() == KeyCode.P) { ctl.togglePin(f.id()); e.consume(); }
            else if (e.getCode() == KeyCode.R) { ctl.showRoute(f.id()); e.consume(); }
        });
    }

    /** Builds a column rendering one node per flight; sortable columns get a clickable header. */
    private TableColumn<Flight, Flight> column(String title, double width, SortKey key,
                                               Function<Flight, Node> renderer) {
        TableColumn<Flight, Flight> col = new TableColumn<>();
        col.setPrefWidth(width);
        col.setSortable(false);
        col.setReorderable(false);
        col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Flight f, boolean empty) {
                super.updateItem(f, empty);
                setText(null);
                setGraphic(empty || f == null ? null : renderer.apply(f));
            }
        });
        if (key == null) {
            col.setText(title);
        } else {
            FontIcon arrow = Ui.icon("mdi2a-arrow-up", 14, "sort-arrow");
            arrow.setVisible(false);
            HBox header = new HBox(6, Ui.label(title, "sort-title"), arrow);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("sort-header");
            header.prefWidthProperty().bind(col.widthProperty().subtract(24));
            header.setOnMouseClicked(e -> ctl.sortBy(key));
            header.setAccessibleText("Sort by " + title.toLowerCase());
            Tooltip.install(header, new Tooltip("Click to sort by " + title.toLowerCase() + "; click again to reverse"));
            arrows.put(key, arrow);
            headers.put(key, header);
            col.setGraphic(header);
        }
        return col;
    }

    private Node pinCell(Flight f) {
        boolean pinned = f.id().equals(state == null ? null : state.pinnedFlightId.get());
        FontIcon pin = Ui.icon(pinned ? "mdi2p-pin" : "mdi2p-pin-outline", 18, "pin-icon");
        pin.getStyleClass().add(pinned ? "pin-on" : "pin-off");
        StackPane p = new StackPane(pin);
        p.getStyleClass().add("pin-hit");
        p.setAccessibleText(pinned ? "Unpin " + f.displayNumber() : "Pin " + f.displayNumber());
        Tooltip.install(p, new Tooltip(pinned ? "Unpin this flight" : "Pin this flight to the top"));
        p.setOnMouseClicked(e -> { ctl.togglePin(f.id()); e.consume(); });
        return p;
    }

    /** Quick pop + fade so a status change draws the eye without being disruptive. */
    private void flash(Node badge) {
        ScaleTransition pop = new ScaleTransition(Duration.millis(450), badge);
        pop.setFromX(1.3);
        pop.setFromY(1.3);
        pop.setToX(1);
        pop.setToY(1);
        FadeTransition fade = new FadeTransition(Duration.millis(700), badge);
        fade.setFromValue(0.15);
        fade.setToValue(1);
        pop.play();
        fade.play();
    }

    void update(List<Flight> rows, AppState st, Flight expanded, String flashId, LocalDateTime lastUpdated) {
        this.state = st;
        this.flashId = flashId;
        if (!rows.equals(table.getItems())) {
            // Keep the keyboard cursor on the same flight when the order changes.
            Flight keep = table.getSelectionModel().getSelectedItem();
            table.getItems().setAll(rows);
            if (keep != null && rows.contains(keep)) table.getSelectionModel().select(keep);
        }
        table.refresh();
        if (flashId != null) {
            PauseTransition clear = new PauseTransition(Duration.millis(400));
            clear.setOnFinished(e -> this.flashId = null);
            clear.play();
        }
        subtitle.setText(rows.size() + " flights  ·  click or press Enter on a row for details  ·  pin your flight to keep it on top");
        updated.setText("Updated " + lastUpdated.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        if (!lastUpdated.equals(lastShownUpdate)) {
            lastShownUpdate = lastUpdated;
            FadeTransition blink = new FadeTransition(Duration.millis(600), liveDot);
            blink.setFromValue(0.15);
            blink.setToValue(1);
            blink.play();
        }
        for (SortKey k : SortKey.values()) {
            FontIcon arrow = arrows.get(k);
            boolean active = st.sortKey.get() == k;
            arrow.setVisible(active);
            arrow.setIconLiteral(st.ascending.get() ? "mdi2a-arrow-up" : "mdi2a-arrow-down");
            headers.get(k).getStyleClass().remove("sort-active");
            if (active) headers.get(k).getStyleClass().add("sort-active");
        }
        updateDetail(expanded, st);
    }

    void focusTable() { table.requestFocus(); if (table.getSelectionModel().isEmpty()) table.getSelectionModel().selectFirst(); }

    private void updateDetail(Flight f, AppState st) {
        if (f != null) {
            boolean pinned = f.id().equals(st.pinnedFlightId.get());
            Button pin = Ui.button(pinned ? "Unpin flight" : "Pin flight", pinned ? "mdi2p-pin-off" : "mdi2p-pin",
                    "action-button");
            pin.setOnAction(e -> ctl.togglePin(f.id()));
            Button route = Ui.button("Route to gate", "mdi2d-directions", "action-button", "primary");
            route.setOnAction(e -> ctl.showRoute(f.id()));
            HBox actions = new HBox(10, pin, route);
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            detailContent.getChildren().setAll(
                    detailItem("TERMINAL", f.terminal()), detailItem("CHECK-IN", f.checkInCounter()),
                    detailItem("BAGGAGE BELT", f.baggageBelt()), detailItem("AIRCRAFT", f.aircraft().describe()),
                    grow, actions);
            if (!f.cancellationReason().isBlank()) {
                detailContent.getChildren().add(4, detailItem("REASON", f.cancellationReason()));
            }
        }
        boolean open = f != null;
        if (open == detailOpen) return;
        detailOpen = open;
        if (detailAnim != null) detailAnim.stop();
        double to = open ? DETAIL_H : 0;
        detailAnim = new Timeline(new KeyFrame(Duration.millis(260),
                new KeyValue(detailWrap.prefHeightProperty(), to, Interpolator.EASE_BOTH),
                new KeyValue(detailWrap.maxHeightProperty(), to, Interpolator.EASE_BOTH)));
        detailAnim.play();
    }

    private Node detailItem(String caption, String value) {
        Label v = Ui.label(value, "detail-value");
        VBox box = new VBox(4, Ui.label(caption, "detail-caption"), v);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
