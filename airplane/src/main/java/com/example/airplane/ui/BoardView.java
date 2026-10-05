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

/**
 * The main departures board: a dense FIDS-style table plus an animated detail drawer.
 * OOP: inheritance - this class extends VBox, so it IS a vertical layout and can be added to a window directly.
 * It only shows data; user actions are passed to the controller (separation of concerns).
 */
class BoardView extends VBox {
    // Height (in pixels) of the detail drawer when it is fully open.
    private static final double DETAIL_H = 112;

    // The controller that handles clicks, pinning, sorting and so on (this view only displays).
    private final FidsController ctl;
    // The table that lists all flights (TableView<Flight> = each row is one Flight object).
    private final TableView<Flight> table = new TableView<>();
    // Grey helper text under the "Departures" title (e.g. number of flights).
    private final Label subtitle = Ui.label("", "board-sub");
    // Text showing when the board was last refreshed.
    private final Label updated = Ui.label("", "board-sub");
    // Small dot that blinks to show the board is live.
    private final Circle liveDot = new Circle(5);
    // Row of details (terminal, check-in...) shown inside the drawer.
    private final HBox detailContent = new HBox(32);
    // Wrapper around the details; its height is animated from 0 to open/close the drawer.
    private final StackPane detailWrap = new StackPane(detailContent);
    // For each sortable column: its sort arrow icon (EnumMap = fast map keyed by an enum).
    private final Map<SortKey, FontIcon> arrows = new EnumMap<>(SortKey.class);
    // For each sortable column: its clickable header box.
    private final Map<SortKey, HBox> headers = new EnumMap<>(SortKey.class);
    // Latest application state (sort order, pinned flight...), supplied in update().
    private AppState state;
    // Id of the flight whose status just changed, so its badge can flash.
    private String flashId;
    // True while the detail drawer is open.
    private boolean detailOpen;
    // The running open/close animation (kept so we can stop it if a new one starts).
    private Timeline detailAnim;
    // The last "updated" time we showed, to know when new data really arrived.
    private LocalDateTime lastShownUpdate;

    /** Constructor: builds the whole board layout once. The controller is passed in (dependency injection). */
    BoardView(FidsController ctl) {
        super(0); // call the VBox constructor: 0 pixels spacing between children
        this.ctl = ctl;
        getStyleClass().add("board"); // CSS class defined in base.css
        setPadding(new Insets(14, 18, 16, 18)); // space inside the view: top, right, bottom, left

        // Left side of the top bar: title with the subtitle underneath (2px apart).
        VBox titles = new VBox(2, Ui.label("Departures", "board-title"), subtitle);
        liveDot.getStyleClass().add("live-dot");
        // Right side of the top bar: dot, the word LIVE and the last-updated text.
        HBox live = new HBox(6, liveDot, Ui.label("LIVE", "live-text"), updated);
        live.setAlignment(Pos.CENTER_RIGHT);
        live.setAccessibleText("Live board"); // read aloud by screen readers
        // An empty stretchy spacer that pushes the "live" box to the far right.
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox top = new HBox(titles, grow, live); // the top bar: titles | spacer | live
        top.setAlignment(Pos.BOTTOM_LEFT);
        top.setPadding(new Insets(0, 0, 12, 0));

        buildTable(); // set up columns and behaviour of the table
        VBox.setVgrow(table, Priority.ALWAYS); // let the table take all spare vertical space

        // Set up the drawer content box: fixed height so it does not resize while animating.
        detailContent.getStyleClass().add("detail-content");
        detailContent.setAlignment(Pos.CENTER_LEFT);
        detailContent.setMinHeight(DETAIL_H);
        detailContent.setMaxHeight(DETAIL_H);
        // The wrapper starts at height 0 so the drawer is closed.
        detailWrap.setAlignment(Pos.TOP_LEFT);
        detailWrap.setPrefHeight(0);
        detailWrap.setMinHeight(0);
        detailWrap.setMaxHeight(0);
        // A clip rectangle cuts off anything outside the wrapper, so a half-open drawer shows only part of the content.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(detailWrap.widthProperty()); // bind = clip size always follows wrapper size
        clip.heightProperty().bind(detailWrap.heightProperty());
        detailWrap.setClip(clip);
        detailWrap.getStyleClass().add("detail-wrap");

        getChildren().addAll(top, table, detailWrap); // stack: top bar, table, drawer
    }

    /** Creates every column of the table and sets row, click and keyboard behaviour. */
    private void buildTable() {
        table.getStyleClass().add("fids-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); // columns share the width, no horizontal scroll
        table.setFixedCellSize(44); // every row is 44px tall (faster to draw)
        table.setPlaceholder(new Label("No flights to display")); // shown when the table is empty
        table.setAccessibleText("Departures board. Use the arrow keys to move, Enter to expand, P to pin, R for route.");

        // Each column(...) call: title, width, sort key (null = not sortable), and a lambda that builds the cell content.
        table.getColumns().add(column("", 44, null, f -> pinCell(f))); // pin icon column
        table.getColumns().add(column("FLIGHT", 100, null, f -> Ui.label(f.displayNumber(), "cell-flight")));
        table.getColumns().add(column("AIRLINE", 200, null, f -> {
            // coloured airline chip + airline name side by side
            HBox b = new HBox(10, Ui.airlineChip(f.airline()), Ui.label(f.airline().name(), "cell-text"));
            b.setAlignment(Pos.CENTER_LEFT);
            return b;
        }));
        table.getColumns().add(column("DESTINATION", 220, null, f -> {
            // city in capitals + airport code
            HBox b = new HBox(10, Ui.label(f.destination().toUpperCase(), "cell-dest"),
                    Ui.label(f.destinationCode(), "cell-code"));
            b.setAlignment(Pos.CENTER_LEFT);
            return b;
        }));
        table.getColumns().add(column("TIME", 130, SortKey.TIME, f -> {
            // scheduled time first
            HBox b = new HBox(8, Ui.label(Ui.HHMM.format(f.scheduledTime()), "cell-time"));
            // if the estimated time differs (flight delayed), also show "-> new time"
            if (!f.estimatedTime().equals(f.scheduledTime())) {
                b.getChildren().add(Ui.label("→ " + Ui.HHMM.format(f.estimatedTime()), "cell-est"));
            }
            b.setAlignment(Pos.CENTER_LEFT);
            return b;
        }));
        table.getColumns().add(column("GATE", 80, SortKey.GATE, f -> {
            Label gate = Ui.label(f.gate(), "cell-gate");
            gate.setTooltip(new Tooltip(f.terminal() + " · Pier " + f.pier())); // hover text with terminal and pier
            return gate;
        }));
        table.getColumns().add(column("STATUS", 170, SortKey.STATUS, f -> {
            Node badge = Ui.statusBadge(f.status()); // coloured status label
            Tooltip.install(badge, new Tooltip(f.status().announcement(f))); // hover shows the full announcement
            if (f.id().equals(flashId)) flash(badge); // animate only the flight that just changed
            return badge;
        }));

        // The row factory creates the row objects; here we customise each row.
        table.setRowFactory(tv -> {
            // Anonymous inner class: a TableRow subclass where we override updateItem (OOP: inheritance + polymorphism).
            TableRow<Flight> row = new TableRow<>() {
                // Called by JavaFX whenever this row is given a different flight (or becomes empty).
                @Override
                protected void updateItem(Flight f, boolean empty) {
                    super.updateItem(f, empty);
                    // Remove old style classes first because rows are reused for different flights.
                    getStyleClass().removeAll("pinned-row", "expanded-row", "inactive-row");
                    if (f == null || empty || state == null) {
                        setAccessibleText(null);
                        return; // nothing to style for an empty row
                    }
                    // Add CSS classes depending on the flight's state (pinned, open drawer, departed/cancelled).
                    if (f.id().equals(state.pinnedFlightId.get())) getStyleClass().add("pinned-row");
                    if (f.id().equals(state.expandedFlightId.get())) getStyleClass().add("expanded-row");
                    if (!f.status().isActive()) getStyleClass().add("inactive-row");
                    // Sentence read by screen readers for this row.
                    setAccessibleText(f.displayNumber() + " to " + f.destination() + ", departs "
                            + Ui.HHMM.format(f.scheduledTime()) + ", gate " + f.gate() + ", " + f.status().label());
                }
            };
            // Event handler (lambda): clicking a non-empty row asks the controller to open/close its details.
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) ctl.onRowClicked(row.getItem().id());
            });
            return row;
        });

        // Keyboard: arrows move (built in), Enter/Space expand, P pins, R shows the route.
        table.setOnKeyPressed(e -> {
            Flight f = table.getSelectionModel().getSelectedItem(); // the highlighted flight
            if (f == null) return; // nothing selected, do nothing
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) { ctl.onRowClicked(f.id()); e.consume(); }
            else if (e.getCode() == KeyCode.P) { ctl.togglePin(f.id()); e.consume(); }
            else if (e.getCode() == KeyCode.R) { ctl.showRoute(f.id()); e.consume(); }
            // e.consume() tells JavaFX we handled the key so nothing else reacts to it
        });
    }

    /**
     * Builds a column rendering one node per flight; sortable columns get a clickable header.
     * The Function parameter is a lambda that turns a Flight into the Node to draw (functional style).
     */
    private TableColumn<Flight, Flight> column(String title, double width, SortKey key,
                                               Function<Flight, Node> renderer) {
        TableColumn<Flight, Flight> col = new TableColumn<>();
        col.setPrefWidth(width);
        col.setSortable(false);   // we do our own sorting through the controller
        col.setReorderable(false); // user cannot drag columns around
        // Each cell's value is the whole Flight object, so the renderer can use any of its fields.
        col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        // Cell factory: creates a cell that draws a graphic (not plain text) using the renderer.
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Flight f, boolean empty) {
                super.updateItem(f, empty);
                setText(null);
                setGraphic(empty || f == null ? null : renderer.apply(f)); // blank if empty, else draw this flight
            }
        });
        if (key == null) {
            col.setText(title); // plain text header for non-sortable columns
        } else {
            // Sortable column: build a custom header with the title and a small arrow.
            FontIcon arrow = Ui.icon("mdi2a-arrow-up", 14, "sort-arrow");
            arrow.setVisible(false); // only the active sort column shows its arrow
            HBox header = new HBox(6, Ui.label(title, "sort-title"), arrow);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("sort-header");
            header.prefWidthProperty().bind(col.widthProperty().subtract(24)); // header stays as wide as the column
            header.setOnMouseClicked(e -> ctl.sortBy(key)); // clicking the header sorts by this column
            header.setAccessibleText("Sort by " + title.toLowerCase());
            Tooltip.install(header, new Tooltip("Click to sort by " + title.toLowerCase() + "; click again to reverse"));
            arrows.put(key, arrow);   // remember them so update() can show/hide the arrow later
            headers.put(key, header);
            col.setGraphic(header);
        }
        return col;
    }

    /** Builds the clickable pin icon for the first column. */
    private Node pinCell(Flight f) {
        // pinned = is this flight the pinned one? (guards against state not being set yet)
        boolean pinned = f.id().equals(state == null ? null : state.pinnedFlightId.get());
        FontIcon pin = Ui.icon(pinned ? "mdi2p-pin" : "mdi2p-pin-outline", 18, "pin-icon"); // filled or outline icon
        pin.getStyleClass().add(pinned ? "pin-on" : "pin-off");
        StackPane p = new StackPane(pin); // bigger clickable area around the icon
        p.getStyleClass().add("pin-hit");
        p.setAccessibleText(pinned ? "Unpin " + f.displayNumber() : "Pin " + f.displayNumber());
        Tooltip.install(p, new Tooltip(pinned ? "Unpin this flight" : "Pin this flight to the top"));
        p.setOnMouseClicked(e -> { ctl.togglePin(f.id()); e.consume(); }); // consume so the row click does not also fire
        return p;
    }

    /** Quick pop + fade so a status change draws the eye without being disruptive. */
    private void flash(Node badge) {
        // Pop: start 30% bigger and shrink to normal size in 450 ms.
        ScaleTransition pop = new ScaleTransition(Duration.millis(450), badge);
        pop.setFromX(1.3);
        pop.setFromY(1.3);
        pop.setToX(1);
        pop.setToY(1);
        // Fade: go from almost invisible to fully visible in 700 ms.
        FadeTransition fade = new FadeTransition(Duration.millis(700), badge);
        fade.setFromValue(0.15);
        fade.setToValue(1);
        pop.play(); // both animations run at the same time
        fade.play();
    }

    /**
     * Called by the controller whenever data changes: refreshes rows, header text, sort arrows and the drawer.
     * (Observer-style: the view is told about changes instead of fetching them.)
     */
    void update(List<Flight> rows, AppState st, Flight expanded, String flashId, LocalDateTime lastUpdated) {
        this.state = st;
        this.flashId = flashId;
        if (!rows.equals(table.getItems())) { // only touch the table if the list really changed
            // Keep the keyboard cursor on the same flight when the order changes.
            Flight keep = table.getSelectionModel().getSelectedItem();
            table.getItems().setAll(rows); // replace all rows
            if (keep != null && rows.contains(keep)) table.getSelectionModel().select(keep);
        }
        table.refresh(); // redraw cells so pins, badges and styles are up to date
        if (flashId != null) {
            // After 400 ms forget the flash id so the flash does not repeat on the next refresh.
            PauseTransition clear = new PauseTransition(Duration.millis(400));
            clear.setOnFinished(e -> this.flashId = null);
            clear.play();
        }
        subtitle.setText(rows.size() + " flights  ·  click or press Enter on a row for details  ·  pin your flight to keep it on top");
        updated.setText("Updated " + lastUpdated.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        if (!lastUpdated.equals(lastShownUpdate)) { // new data arrived since last time
            lastShownUpdate = lastUpdated;
            // Make the green dot blink once.
            FadeTransition blink = new FadeTransition(Duration.millis(600), liveDot);
            blink.setFromValue(0.15);
            blink.setToValue(1);
            blink.play();
        }
        // Update the header of every sortable column.
        for (SortKey k : SortKey.values()) {
            FontIcon arrow = arrows.get(k);
            boolean active = st.sortKey.get() == k; // is the table currently sorted by this column?
            arrow.setVisible(active); // show the arrow only on the active column
            arrow.setIconLiteral(st.ascending.get() ? "mdi2a-arrow-up" : "mdi2a-arrow-down"); // direction of the arrow
            headers.get(k).getStyleClass().remove("sort-active");
            if (active) headers.get(k).getStyleClass().add("sort-active"); // highlight the active header
        }
        updateDetail(expanded, st);
    }

    /** Moves keyboard focus to the table and selects the first row if nothing is selected yet. */
    void focusTable() { table.requestFocus(); if (table.getSelectionModel().isEmpty()) table.getSelectionModel().selectFirst(); }

    /** Fills the detail drawer for flight f (null means no flight is expanded) and opens or closes it. */
    private void updateDetail(Flight f, AppState st) {
        if (f != null) {
            boolean pinned = f.id().equals(st.pinnedFlightId.get());
            // Buttons inside the drawer; the label and icon depend on whether the flight is pinned.
            Button pin = Ui.button(pinned ? "Unpin flight" : "Pin flight", pinned ? "mdi2p-pin-off" : "mdi2p-pin",
                    "action-button");
            pin.setOnAction(e -> ctl.togglePin(f.id()));
            Button route = Ui.button("Route to gate", "mdi2d-directions", "action-button", "primary");
            route.setOnAction(e -> ctl.showRoute(f.id()));
            HBox actions = new HBox(10, pin, route);
            Region grow = new Region(); // spacer pushing the buttons to the right
            HBox.setHgrow(grow, Priority.ALWAYS);
            // Replace the old drawer content with fresh details for this flight.
            detailContent.getChildren().setAll(
                    detailItem("TERMINAL", f.terminal()), detailItem("CHECK-IN", f.checkInCounter()),
                    detailItem("BAGGAGE BELT", f.baggageBelt()), detailItem("AIRCRAFT", f.aircraft().describe()),
                    grow, actions);
            // If the flight has a cancellation reason, insert it at position 4 (after the aircraft item).
            if (!f.cancellationReason().isBlank()) {
                detailContent.getChildren().add(4, detailItem("REASON", f.cancellationReason()));
            }
        }
        boolean open = f != null; // the drawer should be open only when a flight is expanded
        if (open == detailOpen) return; // already in the right state, no animation needed
        detailOpen = open;
        if (detailAnim != null) detailAnim.stop(); // cancel an animation still running
        double to = open ? DETAIL_H : 0; // target height: full height or zero
        // Animate preferred and max height over 260 ms with ease-in/ease-out to slide the drawer.
        detailAnim = new Timeline(new KeyFrame(Duration.millis(260),
                new KeyValue(detailWrap.prefHeightProperty(), to, Interpolator.EASE_BOTH),
                new KeyValue(detailWrap.maxHeightProperty(), to, Interpolator.EASE_BOTH)));
        detailAnim.play();
    }

    /** One caption + value pair for the drawer, e.g. TERMINAL / T1. */
    private Node detailItem(String caption, String value) {
        Label v = Ui.label(value, "detail-value");
        VBox box = new VBox(4, Ui.label(caption, "detail-caption"), v); // caption on top, value below
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
