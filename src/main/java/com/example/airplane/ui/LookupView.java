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
 */
class LookupView extends ScrollPane {
    private final FidsController ctl;
    private final TextField field = new TextField();
    private final VBox resultHolder = new VBox();
    private Flight current;
    private final Label[] digits = new Label[6];
    private Label countdownCaption;

    LookupView(FidsController ctl) {
        this.ctl = ctl;
        getStyleClass().add("lookup-scroll");
        setFitToWidth(true);

        Label title = Ui.label("Find your flight", "board-title");
        Label sub = Ui.label("Enter a flight number or booking reference  ·  Ctrl+F from anywhere", "board-sub");

        field.setPromptText("e.g. BA064 or BK7F3A");
        field.getStyleClass().add("search-field");
        field.setAccessibleText("Flight number or booking reference");
        HBox.setHgrow(field, Priority.ALWAYS);
        Button go = Ui.button("Search", "mdi2m-magnify", "action-button", "primary");
        field.setOnAction(e -> ctl.search(field.getText()));
        go.setOnAction(e -> ctl.search(field.getText()));
        HBox search = new HBox(10, Ui.icon("mdi2m-magnify", 22, "search-icon"), field, go);
        search.setAlignment(Pos.CENTER_LEFT);
        search.getStyleClass().add("search-bar");

        HBox hints = new HBox(8, Ui.label("TRY", "hint-caption"));
        hints.setAlignment(Pos.CENTER_LEFT);
        for (String h : new String[]{"BA064", "BK7F3A", "EK720", "PL48ZT", "KQ412"}) {
            Button b = new Button(h);
            b.getStyleClass().add("chip-button");
            b.setFocusTraversable(false);
            b.setOnAction(e -> { field.setText(h); ctl.search(h); });
            hints.getChildren().add(b);
        }

        VBox content = new VBox(14, new VBox(2, title, sub), search, hints, resultHolder);
        content.setMaxWidth(900);
        content.setPadding(new Insets(16, 18, 18, 18));
        StackPane center = new StackPane(content);
        center.setAlignment(Pos.TOP_CENTER);
        setContent(center);
    }

    void focusSearch() {
        field.requestFocus();
        field.selectAll();
    }

    void update(AppState st, Optional<Flight> flight, Optional<Booking> booking, List<Booking> others,
                boolean pinned, String notice) {
        String q = st.lookupQuery.get();
        current = flight.orElse(null);
        if (q == null || q.isBlank()) {
            resultHolder.getChildren().setAll(message("mdi2a-airplane", "Your flight will appear here",
                    "Search above to see a live countdown, gate and boarding details."));
        } else if (flight.isEmpty()) {
            resultHolder.getChildren().setAll(message("mdi2a-alert", "No flight found for “" + q.trim() + "”",
                    "Check the flight number or booking reference and try again."));
        } else {
            resultHolder.getChildren().setAll(card(flight.get(), booking, others, pinned, notice));
            tick();
        }
    }

    private Node message(String icon, String head, String body) {
        VBox v = new VBox(8, Ui.icon(icon, 44, "empty-icon"), Ui.label(head, "empty-title"), Ui.label(body, "board-sub"));
        v.setAlignment(Pos.CENTER);
        v.getStyleClass().add("empty-card");
        return v;
    }

    private Node card(Flight f, Optional<Booking> booking, List<Booking> others, boolean pinned, String notice) {
        HBox head = new HBox(12, Ui.airlineChip(f.airline()), Ui.label(f.airline().name(), "cell-text"));
        head.setAlignment(Pos.CENTER_LEFT);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox top = new HBox(head, grow, Ui.statusBadge(f.status()));
        top.setAlignment(Pos.CENTER_LEFT);

        Label number = Ui.label(f.displayNumber(), "hero-number");
        Label dest = Ui.label(f.origin().toUpperCase() + "  →  " + f.destination().toUpperCase(), "hero-dest");

        // Split-flap style countdown tiles.
        HBox tiles = new HBox(8);
        tiles.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < 6; i++) {
            digits[i] = Ui.label("0", "flap-text");
            Line seam = new Line(0, 0, 42, 0);
            seam.getStyleClass().add("flap-seam");
            StackPane tile = new StackPane(digits[i], seam);
            tile.getStyleClass().add("flap");
            tiles.getChildren().add(tile);
            if (i == 1 || i == 3) tiles.getChildren().add(Ui.label(":", "flap-colon"));
        }
        countdownCaption = Ui.label("TIME TO DEPARTURE", "detail-caption");
        VBox countdown = new VBox(8, countdownCaption, tiles);

        GridPane grid = new GridPane();
        grid.setHgap(48);
        grid.setVgap(16);
        String[][] info = {
                {"GATE", f.gate()}, {"TERMINAL", f.terminal()}, {"CHECK-IN", f.checkInCounter()},
                {"PASSENGER", booking.map(Booking::passengerName).orElse("—")},
                {"SEAT", booking.map(Booking::seat).orElse("—")},
                {"BOARDING GROUP", booking.map(Booking::boardingGroup).orElse("—")}};
        for (int i = 0; i < info.length; i++) {
            VBox cell = new VBox(4, Ui.label(info[i][0], "detail-caption"),
                    Ui.label(info[i][1], i == 0 ? "hero-gate" : "detail-value"));
            grid.add(cell, i % 3, i / 3);
        }

        Button pin = Ui.button(pinned ? "Unpin flight" : "Pin this flight", pinned ? "mdi2p-pin-off" : "mdi2p-pin",
                "action-button");
        pin.setOnAction(e -> ctl.togglePin(f.id()));
        Button route = Ui.button("Show route to gate", "mdi2d-directions", "action-button", "primary");
        route.setOnAction(e -> ctl.showRoute(f.id()));
        HBox actions = new HBox(10, pin, route);
        if (booking.isPresent() && !booking.get().isCheckedIn()) {
            Button checkIn = Ui.button("Check in", "mdi2c-check-circle", "action-button", "primary");
            checkIn.setDisable(!f.canCheckIn());
            checkIn.setOnAction(e -> ctl.checkIn(booking.get().bookingReference()));
            actions.getChildren().add(checkIn);
        }

        Region sep = new Region();
        sep.getStyleClass().add("divider");
        VBox card = new VBox(18, top, new VBox(0, number, dest), countdown, sep, grid, actions);
        card.getStyleClass().add("your-flight");
        if (notice != null && !notice.isBlank()) card.getChildren().add(Ui.label(notice, "notice"));
        if (booking.isPresent() && booking.get().isCheckedIn()) card.getChildren().add(boardingPass(f, booking.get()));
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
        Random rnd = new Random(b.bookingReference().hashCode());
        for (int i = 0; i < 46; i++) {
            Rectangle r = new Rectangle(1 + rnd.nextInt(3), 54);
            r.getStyleClass().add("barcode-bar");
            bars.getChildren().add(r);
        }
        VBox left = new VBox(6,
                Ui.label("BOARDING PASS", "pass-title"),
                Ui.label(b.passengerName().toUpperCase(), "pass-name"),
                Ui.label(f.displayNumber() + "  ·  " + f.origin().toUpperCase() + " → " + f.destination().toUpperCase(),
                        "cell-text"),
                Ui.label("GATE " + f.gate() + "   SEAT " + b.seat() + "   " + b.boardingGroup().toUpperCase()
                        + "   " + Ui.HHMM.format(f.scheduledTime()), "detail-value"));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
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
        if (current == null || digits[0] == null || countdownCaption == null) return;
        FlightStatus s = current.status();
        if (!s.isActive()) {
            countdownCaption.setText(s == FlightStatus.CANCELLED ? "THIS FLIGHT HAS BEEN CANCELLED" : "THIS FLIGHT HAS DEPARTED");
            for (Label d : digits) d.setText("-");
            return;
        }
        countdownCaption.setText(s == FlightStatus.DELAYED ? "TIME TO ESTIMATED DEPARTURE" : "TIME TO DEPARTURE");
        String t = Ui.countdown(current.estimatedTime()).replace(":", "");
        for (int i = 0; i < 6; i++) digits[i].setText(String.valueOf(t.charAt(i)));
    }
}
