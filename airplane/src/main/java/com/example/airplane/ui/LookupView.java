package com.example.airplane.ui;

import com.example.airplane.model.Booking;
import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;
import com.example.airplane.state.AppState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Personalised view: search by flight number or booking reference, then show a large "Your Flight" card with a
 * countdown, check-in and a boarding pass.
 * OOP: inheritance - extends ScrollPane so the page can scroll when the card is tall.
 */
class LookupView extends ScrollPane {
    // Controller that performs searches, pinning, check-in and route requests for this view.
    private final FidsController ctl;
    // Text box where the user types a flight number or booking reference.
    private final TextField field = new TextField();
    // Empty box that holds whatever result we show (message or flight card); its children get swapped.
    private final VBox resultHolder = new VBox();
    // The flight currently shown, needed by tick() to compute the countdown.
    private Flight current;
    // The six countdown digits (HH MM SS), one Label per split-flap tile.
    private final Label[] digits = new Label[6];
    // Text above the countdown tiles (e.g. "TIME TO DEPARTURE").
    private Label countdownCaption;

    /** Constructor: builds the search bar and the empty result area. */
    LookupView(FidsController ctl) {
        this.ctl = ctl;
        getStyleClass().add("lookup-scroll");
        setFitToWidth(true); // content stretches to the scroll pane width (no sideways scrolling)

        Label title = Ui.label("Find your flight", "board-title");
        Label sub = Ui.label("Enter a flight number or booking reference  ·  Ctrl+F from anywhere", "board-sub");

        field.setPromptText("e.g. BA064 or BK7F3A"); // grey hint text shown while the box is empty
        field.getStyleClass().add("search-field");
        field.setAccessibleText("Flight number or booking reference");
        HBox.setHgrow(field, Priority.ALWAYS); // text box takes all spare width in the row
        Button go = Ui.button("Search", "mdi2m-magnify", "action-button", "primary");
        // Pressing Enter in the box or clicking Search both run the same search (lambda event handlers).
        field.setOnAction(e -> ctl.search(field.getText()));
        go.setOnAction(e -> ctl.search(field.getText()));
        // Search bar row: magnifier icon, text box, Search button.
        HBox search = new HBox(10, Ui.icon("mdi2m-magnify", 22, "search-icon"), field, go);
        search.setAlignment(Pos.CENTER_LEFT);
        search.getStyleClass().add("search-bar");

        // "TRY" row: little buttons with example searches so the demo is easy to use.
        HBox hints = new HBox(8, Ui.label("TRY", "hint-caption"));
        hints.setAlignment(Pos.CENTER_LEFT);
        for (String h : new String[]{"BA064", "BK7F3A", "EK720", "PL48ZT", "KQ412"}) {
            Button b = new Button(h);
            b.getStyleClass().add("chip-button");
            b.setFocusTraversable(false); // Tab key skips these buttons
            b.setOnAction(e -> { field.setText(h); ctl.search(h); }); // fill the box and search
            hints.getChildren().add(b);
        }

        // Page content stacked vertically (14px gap): heading, search bar, examples, results.
        VBox content = new VBox(14, new VBox(2, title, sub), search, hints, resultHolder);
        content.setMaxWidth(900); // do not grow wider than 900px on big screens
        content.setPadding(new Insets(16, 18, 18, 18));
        StackPane center = new StackPane(content); // StackPane lets us centre the content horizontally
        center.setAlignment(Pos.TOP_CENTER);
        setContent(center); // set what the ScrollPane scrolls
    }

    /** Puts the cursor in the search box and selects any old text (used by the Ctrl+F shortcut). */
    void focusSearch() {
        field.requestFocus();
        field.selectAll();
    }

    /**
     * Refreshes the page after a search or data change. Optional means "may be empty" (avoids null).
     * Three cases: nothing searched, nothing found, or a flight found.
     */
    void update(AppState st, Optional<Flight> flight, Optional<Booking> booking, List<Booking> others,
                boolean pinned, String notice) {
        String q = st.lookupQuery.get(); // the text that was searched
        current = flight.orElse(null); // remember the flight (or null) for the countdown
        if (q == null || q.isBlank()) {
            // Case 1: no search yet - show a friendly prompt.
            resultHolder.getChildren().setAll(message("mdi2a-airplane", "Your flight will appear here",
                    "Search above to see a live countdown, gate and boarding details."));
        } else if (flight.isEmpty()) {
            // Case 2: searched but nothing matched.
            resultHolder.getChildren().setAll(message("mdi2a-alert", "No flight found for “" + q.trim() + "”",
                    "Check the flight number or booking reference and try again."));
        } else {
            // Case 3: found - build the card and fill the countdown straight away.
            resultHolder.getChildren().setAll(card(flight.get(), booking, others, pinned, notice));
            tick();
        }
    }

    /** Builds a centred icon + heading + sentence box used for "empty" and "not found" states. */
    private Node message(String icon, String head, String body) {
        VBox v = new VBox(8, Ui.icon(icon, 44, "empty-icon"), Ui.label(head, "empty-title"), Ui.label(body, "board-sub"));
        v.setAlignment(Pos.CENTER);
        v.getStyleClass().add("empty-card");
        return v;
    }

    /** Builds the big "Your Flight" card for flight f (and the passenger's booking, if one was searched). */
    private Node card(Flight f, Optional<Booking> booking, List<Booking> others, boolean pinned, String notice) {
        // Top row: airline chip + name on the left, status badge on the right (spacer in between).
        HBox head = new HBox(12, Ui.airlineChip(f.airline()), Ui.label(f.airline().name(), "cell-text"));
        head.setAlignment(Pos.CENTER_LEFT);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox top = new HBox(head, grow, Ui.statusBadge(f.status()));
        top.setAlignment(Pos.CENTER_LEFT);

        Label number = Ui.label(f.displayNumber(), "hero-number"); // very large flight number
        Label dest = Ui.label(f.origin().toUpperCase() + "  →  " + f.destination().toUpperCase(), "hero-dest");

        // Split-flap style countdown tiles.
        HBox tiles = new HBox(8);
        tiles.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < 6; i++) {
            digits[i] = Ui.label("0", "flap-text"); // one digit; tick() changes the text later
            Line seam = new Line(0, 0, 42, 0); // horizontal line across the tile middle
            seam.getStyleClass().add("flap-seam");
            StackPane tile = new StackPane(digits[i], seam); // stack digit and seam on top of each other
            tile.getStyleClass().add("flap");
            tiles.getChildren().add(tile);
            if (i == 1 || i == 3) tiles.getChildren().add(Ui.label(":", "flap-colon")); // colons: HH:MM:SS
        }
        countdownCaption = Ui.label("TIME TO DEPARTURE", "detail-caption");
        VBox countdown = new VBox(8, countdownCaption, tiles);

        // Grid of facts, 3 per row. A 2D array holds {caption, value} pairs.
        GridPane grid = new GridPane();
        grid.setHgap(48); // horizontal gap between cells
        grid.setVgap(16); // vertical gap between rows
        String[][] info = {
                {"GATE", f.gate()}, {"TERMINAL", f.terminal()}, {"CHECK-IN", f.checkInCounter()},
                // booking.map(...).orElse("—"): use the booking's value if a booking exists, otherwise a dash
                {"PASSENGER", booking.map(Booking::passengerName).orElse("—")},
                {"SEAT", booking.map(Booking::seat).orElse("—")},
                {"BOARDING GROUP", booking.map(Booking::boardingGroup).orElse("—")}};
        for (int i = 0; i < info.length; i++) {
            // First item (gate) gets the larger "hero-gate" style.
            VBox cell = new VBox(4, Ui.label(info[i][0], "detail-caption"),
                    Ui.label(info[i][1], i == 0 ? "hero-gate" : "detail-value"));
            grid.add(cell, i % 3, i / 3); // column = i % 3, row = i / 3 (integer division)
        }

        // Action buttons: pin/unpin and show route.
        Button pin = Ui.button(pinned ? "Unpin flight" : "Pin this flight", pinned ? "mdi2p-pin-off" : "mdi2p-pin",
                "action-button");
        pin.setOnAction(e -> ctl.togglePin(f.id()));
        Button route = Ui.button("Show route to gate", "mdi2d-directions", "action-button", "primary");
        route.setOnAction(e -> ctl.showRoute(f.id()));
        HBox actions = new HBox(10, pin, route);
        // Only offer check-in if a booking was found and it is not yet checked in.
        if (booking.isPresent() && !booking.get().isCheckedIn()) {
            Button checkIn = Ui.button("Check in", "mdi2c-check-circle", "action-button", "primary");
            checkIn.setDisable(!f.canCheckIn()); // greyed out if check-in is not open for this flight
            checkIn.setOnAction(e -> ctl.checkIn(booking.get().bookingReference()));
            actions.getChildren().add(checkIn);
        }

        Region sep = new Region(); // thin divider line (styled by the "divider" CSS class)
        sep.getStyleClass().add("divider");
        // Assemble the card top to bottom.
        VBox card = new VBox(18, top, new VBox(0, number, dest), countdown, sep, grid, actions);
        card.getStyleClass().add("your-flight");
        if (notice != null && !notice.isBlank()) card.getChildren().add(Ui.label(notice, "notice")); // optional message
        // After check-in, show the boarding pass.
        if (booking.isPresent() && booking.get().isCheckedIn()) card.getChildren().add(boardingPass(f, booking.get()));
        // If the passenger has other bookings, list them as buttons that run a new search.
        if (!others.isEmpty()) {
            HBox more = new HBox(8, Ui.label("ALSO BOOKED", "hint-caption"));
            more.setAlignment(Pos.CENTER_LEFT);
            for (Booking o : others) {
                Button b = new Button(o.bookingReference() + "  ·  " + o.linkedFlightNumber());
                b.getStyleClass().add("chip-button");
                b.setFocusTraversable(false);
                b.setOnAction(e -> { field.setText(o.bookingReference()); ctl.search(o.bookingReference()); });
                more.getChildren().add(b);
            }
            card.getChildren().add(more);
        }
        return card;
    }

    /** Printable-style boarding pass with a decorative barcode derived from the booking reference. */
    private Node boardingPass(Flight f, Booking b) {
        HBox bars = new HBox(2);
        bars.setAlignment(Pos.CENTER_LEFT);
        // Seeding Random with the booking's hash means the same booking always gets the same barcode.
        Random rnd = new Random(b.bookingReference().hashCode());
        for (int i = 0; i < 46; i++) {
            Rectangle r = new Rectangle(1 + rnd.nextInt(3), 54); // random width 1-3px, height 54px
            r.getStyleClass().add("barcode-bar");
            bars.getChildren().add(r);
        }
        // Left side: passenger and flight text lines.
        VBox left = new VBox(6,
                Ui.label("BOARDING PASS", "pass-title"),
                Ui.label(b.passengerName().toUpperCase(), "pass-name"),
                Ui.label(f.displayNumber() + "  ·  " + f.origin().toUpperCase() + " → " + f.destination().toUpperCase(),
                        "cell-text"),
                Ui.label("GATE " + f.gate() + "   SEAT " + b.seat() + "   " + b.boardingGroup().toUpperCase()
                        + "   " + Ui.HHMM.format(f.scheduledTime()), "detail-value"));
        Region grow = new Region(); // spacer between left and right sides
        HBox.setHgrow(grow, Priority.ALWAYS);
        // Right side: barcode with the booking reference under it.
        VBox right = new VBox(4, bars, Ui.label(b.bookingReference(), "cell-code"));
        right.setAlignment(Pos.CENTER_RIGHT);
        HBox pass = new HBox(20, left, grow, right);
        pass.setAlignment(Pos.CENTER_LEFT);
        pass.getStyleClass().add("boarding-pass");
        pass.setAccessibleText("Boarding pass for " + b.passengerName() + ", seat " + b.seat() + ", gate " + f.gate());
        return pass;
    }

    /** Called every second by the controller's clock. */
    void tick() {
        // Do nothing if no flight is shown or the card has not been built yet.
        if (current == null || digits[0] == null || countdownCaption == null) return;
        FlightStatus s = current.status();
        if (!s.isActive()) {
            // Flight is finished: explain why and show dashes instead of digits.
            countdownCaption.setText(s == FlightStatus.CANCELLED ? "THIS FLIGHT HAS BEEN CANCELLED" : "THIS FLIGHT HAS DEPARTED");
            for (Label d : digits) d.setText("-");
            return;
        }
        // Delayed flights count down to the new estimated time.
        countdownCaption.setText(s == FlightStatus.DELAYED ? "TIME TO ESTIMATED DEPARTURE" : "TIME TO DEPARTURE");
        // Ui.countdown gives text like "01:23:45"; remove colons to get 6 characters, one per tile.
        String t = Ui.countdown(current.estimatedTime()).replace(":", "");
        for (int i = 0; i < 6; i++) digits[i].setText(String.valueOf(t.charAt(i)));
    }
}
