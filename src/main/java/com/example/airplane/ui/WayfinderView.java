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
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.*;

/** Terminal map drawn with JavaFX shapes; highlights the route from "You are here" to the target gate. */
class WayfinderView extends HBox {
    private static final double W = 1000, H = 640;

    private final FidsController ctl;
    private final Pane map = new Pane();
    private final Map<String, Rectangle> gateBoxes = new HashMap<>();
    private final Group routeLayer = new Group();
    private final ComboBox<Flight> picker = new ComboBox<>();
    private final VBox info = new VBox(14);
    private boolean updating;
    private Timeline dashAnim;
    private PathTransition walker;

    WayfinderView(FidsController ctl) {
        super(0);
        this.ctl = ctl;
        getStyleClass().add("wayfinder");

        drawMap();
        Group scaled = new Group(map);
        StackPane mapHolder = new StackPane(scaled);
        mapHolder.getStyleClass().add("map-holder");
        mapHolder.setMinSize(0, 0);
        mapHolder.setPrefSize(100, 100);
        Runnable fit = () -> {
            double s = Math.min((mapHolder.getWidth() - 24) / W, (mapHolder.getHeight() - 24) / H);
            if (s > 0) { scaled.setScaleX(s); scaled.setScaleY(s); }
        };
        mapHolder.widthProperty().addListener((o, a, b) -> fit.run());
        mapHolder.heightProperty().addListener((o, a, b) -> fit.run());
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(mapHolder.widthProperty());
        clip.heightProperty().bind(mapHolder.heightProperty());
        mapHolder.setClip(clip);
        HBox.setHgrow(mapHolder, Priority.ALWAYS);

        picker.getStyleClass().add("flight-picker");
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.setPromptText("Choose a flight…");
        picker.setButtonCell(pickerCell());
        picker.setCellFactory(lv -> pickerCell());
        picker.valueProperty().addListener((o, a, b) -> {
            if (!updating && b != null) ctl.selectFlight(b.id());
        });

        VBox side = new VBox(16, Ui.label("Route to gate", "board-title"),
                Ui.label("Pick a flight, or select one on the board", "board-sub"), picker, info);
        side.getStyleClass().add("side-panel");
        side.setPadding(new Insets(18));
        side.setPrefWidth(320);
        side.setMinWidth(320);
        getChildren().addAll(mapHolder, side);
    }

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

    private void drawMap() {
        map.setPrefSize(W, H);
        map.setMinSize(W, H);
        map.setMaxSize(W, H);
        Rectangle bg = new Rectangle(W, H);
        bg.getStyleClass().add("map-bg");
        map.getChildren().add(bg);

        // Corridors (piers) and plaza
        map.getChildren().addAll(
                shape(rect(60, 372, 440, 36, 10), "map-corridor"), shape(rect(500, 372, 440, 36, 10), "map-corridor"),
                shape(rect(482, 40, 36, 350, 10), "map-corridor"), shape(rect(420, 340, 160, 100, 18), "map-plaza"),
                shape(rect(460, 448, 80, 36, 8), "map-facility"), shape(rect(300, 500, 400, 110, 16), "map-hall"),
                shape(rect(70, 500, 200, 110, 16), "map-facility"), shape(rect(730, 500, 200, 110, 16), "map-facility"));

        map.getChildren().addAll(
                text("CENTRAL PLAZA", 500, 418, "map-label-sm"), text("Food court  ·  Duty free", 500, 356, "map-label-xs"),
                text("SECURITY", 500, 471, "map-label-sm"), text("DEPARTURES HALL", 500, 535, "map-label"),
                text("Check-in · Information", 500, 556, "map-label-xs"),
                text("CHECK-IN ZONES", 170, 555, "map-label-sm"), text("LOUNGES & RETAIL", 830, 555, "map-label-sm"),
                text("TERMINAL 1  ·  PIER A", 200, 450, "map-terminal"),
                text("TERMINAL 3  ·  PIER C", 800, 450, "map-terminal"),
                text("TERMINAL 2  ·  PIER B", 620, 70, "map-terminal"));

        // Gates
        for (String pier : new String[]{"A", "B", "C"}) {
            for (int n = 1; n <= 8; n++) {
                String g = pier + n;
                GateSlot s = GateMap.slot(g);
                Line stub = new Line(s.door().x(), s.door().y(), s.box().x(), s.box().y());
                stub.getStyleClass().add("map-stub");
                Rectangle box = rect(s.box().x() - 22, s.box().y() - 17, 44, 34, 8);
                box.getStyleClass().add("map-gate");
                gateBoxes.put(g, box);
                map.getChildren().addAll(stub, box, text(g, s.box().x(), s.box().y() + 5, "map-gate-label"));
            }
        }

        map.getChildren().add(routeLayer);

        // "You are here" marker with pulse
        Circle pulse = new Circle(YOU().x(), YOU().y(), 10);
        pulse.getStyleClass().add("map-you-pulse");
        Circle dot = new Circle(YOU().x(), YOU().y(), 9);
        dot.getStyleClass().add("map-you");
        Timeline pulseAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pulse.radiusProperty(), 9), new KeyValue(pulse.opacityProperty(), 0.7)),
                new KeyFrame(Duration.seconds(1.6), new KeyValue(pulse.radiusProperty(), 30), new KeyValue(pulse.opacityProperty(), 0)));
        pulseAnim.setCycleCount(Animation.INDEFINITE);
        pulseAnim.play();
        Text you = text("YOU ARE HERE", YOU().x() + 75, YOU().y() + 5, "map-you-label");
        map.getChildren().addAll(pulse, dot, you);
    }

    private static Pt YOU() { return GateMap.YOU_ARE_HERE; }

    private static Rectangle rect(double x, double y, double w, double h, double arc) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(arc * 2);
        r.setArcHeight(arc * 2);
        return r;
    }

    private static Node shape(Shape s, String style) {
        s.getStyleClass().add(style);
        return s;
    }

    private static Text text(String s, double cx, double y, String style) {
        Text t = new Text(s);
        t.getStyleClass().add(style);
        t.setTextAlignment(TextAlignment.CENTER);
        t.applyCss();
        t.setX(cx - t.getLayoutBounds().getWidth() / 2);
        t.setY(y);
        t.setMouseTransparent(true);
        return t;
    }

    // ---- dynamic part -------------------------------------------------------------------------

    void update(Flight target, List<Flight> all) {
        updating = true;
        if (picker.getItems().size() != all.size()) picker.getItems().setAll(all);
        picker.setValue(target);
        updating = false;

        gateBoxes.values().forEach(r -> r.getStyleClass().remove("map-gate-target"));
        routeLayer.getChildren().clear();
        if (dashAnim != null) dashAnim.stop();
        if (walker != null) walker.stop();

        if (target == null || GateMap.slot(target.gate()) == null) {
            info.getChildren().setAll(Ui.label("No flight selected", "empty-title"),
                    Ui.label("Choose a flight above, or click a row on the departures board.", "board-sub"));
            return;
        }

        gateBoxes.get(target.gate()).getStyleClass().add("map-gate-target");
        List<Pt> pts = GateMap.route(target.gate());
        Polyline line = new Polyline();
        Path path = new Path();
        for (int i = 0; i < pts.size(); i++) {
            line.getPoints().addAll(pts.get(i).x(), pts.get(i).y());
            path.getElements().add(i == 0 ? new MoveTo(pts.get(i).x(), pts.get(i).y())
                    : new LineTo(pts.get(i).x(), pts.get(i).y()));
        }
        line.getStyleClass().add("map-route");
        line.getStrokeDashArray().setAll(16.0, 12.0);
        dashAnim = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(line.strokeDashOffsetProperty(), 28)),
                new KeyFrame(Duration.seconds(1.2), new KeyValue(line.strokeDashOffsetProperty(), 0)));
        dashAnim.setCycleCount(Animation.INDEFINITE);
        dashAnim.play();

        Circle walkerDot = new Circle(7);
        walkerDot.getStyleClass().add("map-walker");
        double metres = GateMap.lengthMetres(pts);
        walker = new PathTransition(Duration.seconds(Math.max(3, metres / 40)), path, walkerDot);
        walker.setCycleCount(Animation.INDEFINITE);
        walker.setInterpolator(Interpolator.LINEAR);
        routeLayer.getChildren().addAll(line, walkerDot);
        walker.play();

        int minutes = Math.max(1, (int) Math.round(metres / 75.0));
        char pier = target.gate().charAt(0);
        info.getChildren().setAll(
                summary(target),
                stat("WALKING DISTANCE", Math.round(metres / 10.0) * 10 + " m", "≈ " + minutes + " min on foot"),
                Ui.label("DIRECTIONS", "detail-caption"),
                step("mdi2m-map-marker", "Start at the Departures Hall"),
                step("mdi2d-door-closed", "Pass through Security"),
                step("mdi2a-airplane", "Enter the Central Plaza"),
                step("mdi2d-directions", "Follow Pier " + pier + " (" + target.terminal() + ")"),
                step("mdi2c-check-circle", "Arrive at Gate " + target.gate()));
    }

    private Node summary(Flight f) {
        HBox top = new HBox(10, Ui.airlineChip(f.airline()),
                Ui.label(f.displayNumber() + "  ·  " + f.destination(), "detail-title"));
        top.setAlignment(Pos.CENTER_LEFT);
        HBox gate = new HBox(24, stat("GATE", f.gate(), f.terminal()), Ui.statusBadge(f.status()));
        gate.setAlignment(Pos.CENTER_LEFT);
        VBox v = new VBox(12, top, gate);
        v.getStyleClass().add("mini-card");
        return v;
    }

    private VBox stat(String caption, String value, String sub) {
        VBox v = new VBox(2, Ui.label(caption, "detail-caption"), Ui.label(value, "hero-gate"),
                Ui.label(sub, "board-sub"));
        return v;
    }

    private Node step(String icon, String text) {
        HBox h = new HBox(10, Ui.icon(icon, 18, "step-icon"), Ui.label(text, "cell-text"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }
}
