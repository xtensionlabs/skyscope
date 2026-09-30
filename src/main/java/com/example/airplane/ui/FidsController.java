package com.example.airplane.ui;

import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightRepository;
import com.example.airplane.model.FlightStatus;
import com.example.airplane.service.SimulationService;
import com.example.airplane.state.AppState;
import com.example.airplane.state.AppState.Screen;
import com.example.airplane.state.AppState.SortKey;
import com.example.airplane.state.AppState.Theme;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Presenter: owns the views, reacts to user actions by mutating {@link AppState}, and funnels every
 * visual update through the single {@link #refresh()} method.
 */
public class FidsController {

    private final AppState state = new AppState();
    private final FlightRepository repo = new FlightRepository(LocalDateTime.now());
    private final SimulationService simulation = new SimulationService(repo, this::onSimulationChange);

    private final HeaderBar header = new HeaderBar(this);
    private final BoardView board = new BoardView(this);
    private final LookupView lookup = new LookupView(this);
    private final WayfinderView wayfinder = new WayfinderView(this);
    private final BannerView banner = new BannerView();
    private final Map<Screen, Node> screens = new EnumMap<>(Screen.class);
    private final StackPane root = new StackPane();

    private Scene scene;
    private Theme appliedTheme;
    private Screen shownScreen;
    private String flashId;
    private int suspended;

    public FidsController() {
        screens.put(Screen.BOARD, board);
        screens.put(Screen.LOOKUP, lookup);
        screens.put(Screen.MAP, wayfinder);
        StackPane content = new StackPane(board, lookup, wayfinder);
        BorderPane layout = new BorderPane(content);
        layout.setTop(header);
        layout.getStyleClass().add("app-root");
        StackPane.setAlignment(banner, javafx.geometry.Pos.TOP_CENTER);
        StackPane.setMargin(banner, new javafx.geometry.Insets(74, 0, 0, 0));
        root.getChildren().addAll(layout, banner);

        state.onChange(() -> { if (suspended == 0) refresh(); });
    }

    public StackPane root() { return root; }

    public void start(Scene scene) {
        this.scene = scene;
        refresh();
        simulation.start();
        // One-second clock drives the header and countdown; every 30s we also refresh to retire old flights.
        int[] ticks = {0};
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            header.tickClock();
            lookup.tick();
            if (++ticks[0] % 30 == 0) refresh();
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    public void stop() { simulation.stop(); }

    // ---- user actions (all mutate state; state changes trigger refresh) ----------------------

    void sortBy(SortKey key) {
        batch(() -> {
            if (state.sortKey.get() == key) state.ascending.set(!state.ascending.get());
            else { state.sortKey.set(key); state.ascending.set(true); }
        });
    }

    void togglePin(String id) {
        state.pinnedFlightId.set(Objects.equals(state.pinnedFlightId.get(), id) ? null : id);
    }

    void onRowClicked(String id) {
        batch(() -> {
            state.expandedFlightId.set(Objects.equals(state.expandedFlightId.get(), id) ? null : id);
            state.selectedFlightId.set(id);
        });
    }

    void showRoute(String id) {
        batch(() -> { state.selectedFlightId.set(id); state.screen.set(Screen.MAP); });
    }

    void selectFlight(String id) { state.selectedFlightId.set(id); }
    void showScreen(Screen s) { state.screen.set(s); }
    void search(String q) { state.lookupQuery.set(q == null ? "" : q.trim()); }

    private void batch(Runnable r) {
        suspended++;
        try { r.run(); } finally { suspended--; }
        refresh();
    }

    // ---- simulation --------------------------------------------------------------------------

    private void onSimulationChange(SimulationService.Change c) {
        Flight f = c.flight();
        if (c.gateChanged()) {
            banner.show("GATE CHANGE  ·  " + f.displayNumber(),
                    f.destination() + " now departs from Gate " + f.gate() + " (was " + c.oldGate() + ")");
        }
        flashId = f.id();
        refresh();
        flashId = null;
    }

    // ---- the one redraw method ---------------------------------------------------------------

    /** Re-renders every view from the current state + data. */
    void refresh() {
        applyTheme();
        header.update(state);
        showScreen();

        List<Flight> rows = boardRows();
        Flight expanded = repo.byId(state.expandedFlightId.get()).filter(rows::contains).orElse(null);
        board.update(rows, state, expanded, flashId);

        Flight found = repo.lookup(state.lookupQuery.get()).orElse(null);
        lookup.update(state, java.util.Optional.ofNullable(found),
                found == null ? java.util.Optional.empty() : repo.bookingFor(found.id()),
                found != null && found.id().equals(state.pinnedFlightId.get()));

        wayfinder.update(routeTarget(), repo.flights());
    }

    /** Pinned flight first, then the rest in the chosen order; long-departed flights are retired. */
    private List<Flight> boardRows() {
        String pinned = state.pinnedFlightId.get();
        Comparator<Flight> cmp = switch (state.sortKey.get()) {
            case TIME -> Comparator.comparing(Flight::scheduledTime);
            case GATE -> Comparator.comparing(FidsController::gateKey);
            case STATUS -> Comparator.comparing((Flight f) -> f.status().ordinal());
        };
        if (!state.ascending.get()) cmp = cmp.reversed();
        cmp = cmp.thenComparing(Flight::scheduledTime);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        return repo.flights().stream()
                .filter(f -> f.id().equals(pinned)
                        || !(f.status() == FlightStatus.DEPARTED && f.estimatedTime().isBefore(cutoff)))
                .sorted(Comparator.comparing((Flight f) -> !f.id().equals(pinned)).thenComparing(cmp))
                .toList();
    }

    /** "B2" -> "B02" so gates sort naturally. */
    private static String gateKey(Flight f) {
        String g = f.gate();
        return g.substring(0, 1) + String.format("%02d", Integer.parseInt(g.substring(1)));
    }

    /** Selected flight drives the map; falls back to the pinned flight. */
    private Flight routeTarget() {
        return repo.byId(state.selectedFlightId.get())
                .or(() -> repo.byId(state.pinnedFlightId.get())).orElse(null);
    }

    private void applyTheme() {
        Theme t = state.theme.get();
        if (scene == null || t == appliedTheme) return;
        appliedTheme = t;
        scene.getStylesheets().setAll(
                Objects.requireNonNull(getClass().getResource("/css/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/light.css")).toExternalForm());
    }

    private void showScreen() {
        Screen s = state.screen.get();
        if (s == shownScreen) return;
        boolean first = shownScreen == null;
        shownScreen = s;
        screens.forEach((k, n) -> { n.setVisible(k == s); n.setManaged(k == s); });
        if (!first) {
            FadeTransition ft = new FadeTransition(Duration.millis(220), screens.get(s));
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        }
    }
}
