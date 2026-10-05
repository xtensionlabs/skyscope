package com.example.airplane.ui;

import com.example.airplane.model.Flight;
import com.example.airplane.service.GateMap;
import com.example.airplane.service.GateMap.GateSlot;
import com.example.airplane.service.GateMap.Pt;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.*;

/**
 * Terminal map drawn with JavaFX shapes; highlights the route from "You are here" to the target gate.
 * OOP: inheritance (extends HBox: map on the left, side panel on the right). Route maths lives in GateMap (service class).
 */
class WayfinderView extends HBox {
    // Size of the map drawing in its own units (width, height). The map is scaled to fit the window.
    private static final double W = 1000, H = 640;

    // Controller used when the user picks a flight in the drop-down.
    private final FidsController ctl;
    // The drawing surface; shapes are placed at exact x/y positions.
    private final Pane map = new Pane();
    // Gate name (e.g. "A3") -> its rectangle, so we can highlight the target gate quickly.
    private final Map<String, Rectangle> gateBoxes = new HashMap<>();
    // Gate name -> its text, so the highlighted gate can use dark text on yellow.
    private final Map<String, Text> gateTexts = new HashMap<>();
    // Group that holds the route line and walking dot; cleared and redrawn on every update.
    private final Group routeLayer = new Group();
    // Drop-down to choose a flight.
    private final ComboBox<Flight> picker = new ComboBox<>();
    // Right-hand panel content (summary, distance, directions).
    private final VBox info = new VBox(14);
    // Guard flag: true while update() sets the picker by code, so the listener does not react.
    private boolean updating;
    // Animation of the moving dashes on the route line.
    private Timeline dashAnim;
    // Animation of the dot walking along the route.
    private PathTransition walker;

    /** Constructor: draws the map once and builds the layout (map + side panel). */
    WayfinderView(FidsController ctl) {
        super(0);
        this.ctl = ctl;
        getStyleClass().add("wayfinder");

        drawMap(); // draw all fixed parts of the map
        // A Group is wrapped around the map because scaling a Group also changes its layout size,
        // so the map can shrink or grow to fit the available space.
        Group scaled = new Group(map);
        StackPane mapHolder = new StackPane(scaled);
        mapHolder.getStyleClass().add("map-holder");
        mapHolder.setMinSize(0, 0); // allow it to shrink below the map's own size
        mapHolder.setPrefSize(100, 100);
        // fit: Runnable lambda that works out the biggest scale where the whole map still fits (24px margin).
        Runnable fit = () -> {
            double s = Math.min((mapHolder.getWidth() - 24) / W, (mapHolder.getHeight() - 24) / H);
            if (s > 0) { scaled.setScaleX(s); scaled.setScaleY(s); }
        };
        // Re-run fit whenever the holder is resized (listeners on the width/height properties).
        mapHolder.widthProperty().addListener((o, a, b) -> fit.run());
        mapHolder.heightProperty().addListener((o, a, b) -> fit.run());
        // Clip so the scaled map cannot draw outside its holder.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(mapHolder.widthProperty());
        clip.heightProperty().bind(mapHolder.heightProperty());
        mapHolder.setClip(clip);
        HBox.setHgrow(mapHolder, Priority.ALWAYS); // map takes all spare width

        picker.getStyleClass().add("flight-picker");
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.setPromptText("Choose a flight…");
        picker.setButtonCell(pickerCell());
        picker.setCellFactory(lv -> pickerCell());
        // When the user chooses a flight (not when code sets it), tell the controller.
        picker.valueProperty().addListener((o, a, b) -> {
            if (!updating && b != null) ctl.selectFlight(b.id());
        });

        // Right-hand side panel: title, hint, picker and the dynamic info box. Fixed 320px wide.
        VBox side = new VBox(16, Ui.label("Route to gate", "board-title"),
                Ui.label("Pick a flight, or select one on the board", "board-sub"), picker, info);
        side.getStyleClass().add("side-panel");
        side.setPadding(new Insets(18));
        side.setPrefWidth(320);
        side.setMinWidth(320);
        getChildren().addAll(mapHolder, side);
    }

    /** Creates a cell that shows a flight as "number · destination · Gate X". */
    private ListCell<Flight> pickerCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Flight f, boolean empty) {
                super.updateItem(f, empty);
                setText(empty || f == null ? null
                        : f.displayNumber() + "  ·  " + f.destination() + "  ·  Gate " + f.gate());
            }
        };
    }

    // ---- static map drawing -------------------------------------------------------------------

    /** Draws everything on the map that never changes: background, corridors, labels, gates, "you are here". */
    private void drawMap() {
        // Fix the map to exactly W x H.
        map.setPrefSize(W, H);
        map.setMinSize(W, H);
        map.setMaxSize(W, H);
        Rectangle bg = new Rectangle(W, H); // background rectangle
        bg.getStyleClass().add("map-bg");
        map.getChildren().add(bg);

        // Corridors (piers) and plaza
        // rect(x, y, width, height, cornerRadius); the style name picks the colour from CSS.
        map.getChildren().addAll(
                shape(rect(60, 372, 440, 36, 0), "map-corridor"), shape(rect(500, 372, 440, 36, 0), "map-corridor"),
                shape(rect(482, 40, 36, 350, 0), "map-corridor"), shape(rect(420, 340, 160, 100, 0), "map-plaza"),
                shape(rect(460, 448, 80, 36, 0), "map-facility"), shape(rect(300, 500, 400, 110, 0), "map-hall"),
                shape(rect(70, 500, 200, 110, 0), "map-facility"), shape(rect(730, 500, 200, 110, 0), "map-facility"));

        // Text labels: text(words, centreX, baselineY, style).
        // The route always runs down the middle (x = 500), so labels near it sit to one side of that line,
        // and the pier names sit just outside the rows of gate boxes so they never overlap a gate.
        map.getChildren().addAll(
                text("PLAZA", 540, 418, "map-label-sm"), text("Food · Shops", 540, 362, "map-label-xs"),
                text("SECURITY", 592, 471, "map-label-sm"), text("DEPARTURES HALL", 400, 535, "map-label"),
                text("Check-in · Information", 400, 556, "map-label-xs"),
                text("CHECK-IN ZONES", 170, 555, "map-label-sm"), text("LOUNGES & RETAIL", 830, 555, "map-label-sm"),
                text("TERMINAL 1  ·  PIER A", 280, 312, "map-terminal"),
                text("TERMINAL 3  ·  PIER C", 720, 312, "map-terminal"),
                text("TERMINAL 2  ·  PIER B", 500, 28, "map-terminal"));

        // Gates
        List<Node> gateLabels = new ArrayList<>(); // gate names, added to the map after the route layer
        // Nested loops: 3 piers (A, B, C) x 8 gates each = 24 gates (A1..A8, B1..B8, C1..C8).
        for (String pier : new String[]{"A", "B", "C"}) {
            for (int n = 1; n <= 8; n++) {
                String g = pier + n; // gate name such as "A3"
                GateSlot s = GateMap.slot(g); // looks up the gate's door and box positions
                Line stub = new Line(s.door().x(), s.door().y(), s.box().x(), s.box().y()); // link from corridor to gate
                stub.getStyleClass().add("map-stub");
                Rectangle box = rect(s.box().x() - 22, s.box().y() - 17, 44, 34, 0); // square gate box centred on its position
                box.getStyleClass().add("map-gate");
                gateBoxes.put(g, box); // remember for highlighting later
                map.getChildren().addAll(stub, box);
                Text label = text(g, s.box().x(), s.box().y() + 5, "map-gate-label");
                gateLabels.add(label);
                gateTexts.put(g, label); // remember so the target gate's text colour can change
            }
        }

        map.getChildren().add(routeLayer); // empty for now; update() fills it (added after gates so it draws on top)
        map.getChildren().addAll(gateLabels); // gate names on top of the route line

        // "You are here" marker with pulse
        Circle pulse = new Circle(YOU().x(), YOU().y(), 10); // outer ring that grows and fades
        pulse.getStyleClass().add("map-you-pulse");
        Circle dot = new Circle(YOU().x(), YOU().y(), 9);    // solid red dot
        dot.getStyleClass().add("map-you");
        // Animation: from t=0 radius 9 / 70% visible, to t=1.6s radius 30 / invisible; repeats forever.
        Timeline pulseAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pulse.radiusProperty(), 9), new KeyValue(pulse.opacityProperty(), 0.7)),
                new KeyFrame(Duration.seconds(1.6), new KeyValue(pulse.radiusProperty(), 30), new KeyValue(pulse.opacityProperty(), 0)));
        pulseAnim.setCycleCount(Animation.INDEFINITE);
        pulseAnim.play();
        Text you = text("YOU ARE HERE", YOU().x() + 75, YOU().y() + 5, "map-you-label");
        map.getChildren().addAll(pulse, dot, you);
    }

    /** Shortcut for the fixed "You are here" point (Pt is a record with x and y). */
    private static Pt YOU() { return GateMap.YOU_ARE_HERE; }

    /** Helper: makes a rounded rectangle. Arc width/height are set to 2 x radius, JavaFX's way to round corners. */
    private static Rectangle rect(double x, double y, double w, double h, double arc) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(arc * 2);
        r.setArcHeight(arc * 2);
        return r;
    }

    /** Helper: gives any Shape a CSS style class and returns it as a Node (polymorphism: works for every Shape). */
    private static Node shape(Shape s, String style) {
        s.getStyleClass().add(style);
        return s;
    }

    /** Helper: makes a text label centred on x=cx. */
    private static Text text(String s, double cx, double y, String style) {
        Text t = new Text(s);
        t.getStyleClass().add(style);
        t.setTextAlignment(TextAlignment.CENTER);
        t.applyCss(); // apply CSS now so the font size is known before measuring
        t.setX(cx - t.getLayoutBounds().getWidth() / 2); // move left by half the width to centre it
        t.setY(y);
        t.setMouseTransparent(true); // clicks pass through the text
        return t;
    }

    // ---- dynamic part -------------------------------------------------------------------------

    /** Called when the chosen flight changes: highlights its gate, draws the route and fills the side panel. */
    void update(Flight target, List<Flight> all) {
        updating = true; // so the picker listener ignores the next change
        if (picker.getItems().size() != all.size()) picker.getItems().setAll(all);
        picker.setValue(target);
        updating = false;

        // Reset the previous route: remove old highlight, old shapes and stop old animations.
        gateBoxes.values().forEach(r -> r.getStyleClass().remove("map-gate-target"));
        gateTexts.values().forEach(t -> t.getStyleClass().remove("map-gate-label-target"));
        routeLayer.getChildren().clear();
        if (dashAnim != null) dashAnim.stop();
        if (walker != null) walker.stop();

        // No flight chosen, or its gate is not on the map: show a message and stop.
        if (target == null || GateMap.slot(target.gate()) == null) {
            info.getChildren().setAll(Ui.label("No flight selected", "empty-title"),
                    Ui.label("Choose a flight above, or click a row on the departures board.", "board-sub"));
            return;
        }

        gateBoxes.get(target.gate()).getStyleClass().add("map-gate-target"); // highlight destination gate
        gateTexts.get(target.gate()).getStyleClass().add("map-gate-label-target");
        List<Pt> pts = GateMap.route(target.gate()); // list of corner points from "you are here" to the gate
        Polyline line = new Polyline(); // the visible route (joined straight segments)
        Path path = new Path();         // the same points as a path for the walking dot to follow
        for (int i = 0; i < pts.size(); i++) {
            line.getPoints().addAll(pts.get(i).x(), pts.get(i).y());
            // first point: MoveTo (start); other points: LineTo (draw a segment to it)
            path.getElements().add(i == 0 ? new MoveTo(pts.get(i).x(), pts.get(i).y())
                    : new LineTo(pts.get(i).x(), pts.get(i).y()));
        }
        line.getStyleClass().add("map-route");
        line.getStrokeDashArray().setAll(16.0, 12.0); // dashed line: 16px dash, 12px gap
        // Moving the dash offset from 28 to 0 (= one dash+gap period) makes the dashes appear to flow; loops forever.
        dashAnim = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(line.strokeDashOffsetProperty(), 28)),
                new KeyFrame(Duration.seconds(1.2), new KeyValue(line.strokeDashOffsetProperty(), 0)));
        dashAnim.setCycleCount(Animation.INDEFINITE);
        dashAnim.play();

        Circle walkerDot = new Circle(7); // the little dot that "walks"
        walkerDot.getStyleClass().add("map-walker");
        double metres = GateMap.lengthMetres(pts); // total route length
        // Duration depends on distance (40 metres per second) but is never shorter than 3 seconds.
        walker = new PathTransition(Duration.seconds(Math.max(3, metres / 40)), path, walkerDot);
        walker.setCycleCount(Animation.INDEFINITE);
        walker.setInterpolator(Interpolator.LINEAR); // constant walking speed
        routeLayer.getChildren().addAll(line, walkerDot);
        walker.play();

        // Walking time estimate: 75 metres per minute, at least 1 minute.
        int minutes = Math.max(1, (int) Math.round(metres / 75.0));
        char pier = target.gate().charAt(0); // first letter of the gate is the pier (A, B or C)
        // Rebuild the side panel: summary card, distance, then step-by-step directions.
        info.getChildren().setAll(
                summary(target),
                stat("WALKING DISTANCE", Math.round(metres / 10.0) * 10 + " m", "≈ " + minutes + " min on foot"), // rounded to nearest 10 m
                Ui.label("DIRECTIONS", "detail-caption"),
                step("mdi2m-map-marker", "Start at the Departures Hall"),
                step("mdi2d-door-closed", "Pass through Security"),
                step("mdi2a-airplane", "Enter the Central Plaza"),
                step("mdi2d-directions", "Follow Pier " + pier + " (" + target.terminal() + ")"),
                step("mdi2c-check-circle", "Arrive at Gate " + target.gate()));
    }

    /** Small card with airline, flight number, destination, gate and status. */
    private Node summary(Flight f) {
        HBox top = new HBox(10, Ui.airlineLogo(f.airline()),
                Ui.label(f.displayNumber() + "  ·  " + f.destination(), "detail-title"));
        top.setAlignment(Pos.CENTER_LEFT);
        HBox gate = new HBox(24, stat("GATE", f.gate(), f.terminal()), Ui.statusBadge(f.status()));
        gate.setAlignment(Pos.CENTER_LEFT);
        VBox v = new VBox(12, top, gate);
        v.getStyleClass().add("mini-card");
        return v;
    }

    /** A three-line block: small caption, big value, and a grey note underneath. */
    private VBox stat(String caption, String value, String sub) {
        VBox v = new VBox(2, Ui.label(caption, "detail-caption"), Ui.label(value, "hero-gate"),
                Ui.label(sub, "board-sub"));
        return v;
    }

    /** One line of the directions list: an icon followed by text. */
    private Node step(String icon, String text) {
        HBox h = new HBox(10, Ui.icon(icon, 18, "step-icon"), Ui.label(text, "cell-text"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }
}
