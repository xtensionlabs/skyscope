package com.example.airplane.ui;

import com.example.airplane.data.JsonDataSource;
import com.example.airplane.exception.FidsException;
import com.example.airplane.exception.FlightNotFoundException;
import com.example.airplane.model.Booking;
import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightRepository;
import com.example.airplane.model.FlightStatus;
import com.example.airplane.notification.Notification;
import com.example.airplane.service.*;
import com.example.airplane.service.command.AdminCommand;
import com.example.airplane.state.AppState;
import com.example.airplane.state.AppState.Screen;
import com.example.airplane.state.AppState.SortKey;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Presenter: owns the views, reacts to user actions by mutating {@link AppState}, listens to the
 * {@link FlightService} for data changes, and funnels every visual update through {@link #refresh()}.
 */
public class FidsController implements FlightListener {
    private static final String STAFF_PASSWORD = "staff123";

    private final FlightService service = createService();
    private final AppState state = new AppState();
    private final SimulationService simulation = new SimulationService(service);
    private final List<StatusTransitionStrategy> strategies =
            List.of(new RandomProgressionStrategy(), new TimeAwareStrategy());

    private final HeaderBar header = new HeaderBar(this);
    private final BoardView board = new BoardView(this);
    private final LookupView lookup = new LookupView(this);
    private final WayfinderView wayfinder = new WayfinderView(this);
    private final AdminView admin = new AdminView(this, strategies);
    private final BannerView banner = new BannerView();
    private final Map<Screen, Node> screens = new EnumMap<>(Screen.class);
    private final StackPane root = new StackPane();

    private Scene scene;
    private Screen shownScreen;
    private String flashId;
    private String lookupNotice;
    private LocalDateTime lastUpdated = LocalDateTime.now();
    private int suspended;

    public FidsController() {
        screens.put(Screen.BOARD, board);
        screens.put(Screen.LOOKUP, lookup);
        screens.put(Screen.MAP, wayfinder);
        screens.put(Screen.ADMIN, admin);
        StackPane content = new StackPane(board, lookup, wayfinder, admin);
        BorderPane layout = new BorderPane(content);
        layout.setTop(header);
        layout.getStyleClass().add("app-root");
        StackPane.setAlignment(banner, Pos.TOP_CENTER);
        StackPane.setMargin(banner, new Insets(74, 0, 0, 0));
        root.getChildren().addAll(layout, banner);

        simulation.setStrategy(strategies.get(0));
        service.addListener(this);
        state.onChange(() -> { if (suspended == 0) refresh(); });
    }

    /** Saved JSON data if possible; otherwise fall back to the in-memory sample data. */
    private static FlightService createService() {
        try {
            return new FlightService(new JsonDataSource(Path.of("data", "fids.json")));
        } catch (IOException | RuntimeException e) {
            System.err.println("Using in-memory sample data (" + e.getMessage() + ")");
            try {
                return new FlightService(new FlightRepository());
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    public StackPane root() { return root; }

    public void start(Scene scene) {
        this.scene = scene;
        scene.getStylesheets().setAll(
                Objects.requireNonNull(getClass().getResource("/css/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/light.css")).toExternalForm());
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+F"), () -> { showScreen(Screen.LOOKUP); focusLookup(); });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+1"), () -> { showScreen(Screen.BOARD); Platform.runLater(board::focusTable); });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+2"), () -> { showScreen(Screen.LOOKUP); focusLookup(); });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+3"), () -> showScreen(Screen.MAP));
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+4"), () -> { if (state.staffMode.get()) showScreen(Screen.ADMIN); });
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

    public void stop() {
        simulation.stop();
        service.persist();
    }

    private void focusLookup() { Platform.runLater(lookup::focusSearch); }

    SimulationService simulation() { return simulation; }

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
    void toggleLargeText() { state.largeText.set(!state.largeText.get()); }
    void toggleHighContrast() { state.highContrast.set(!state.highContrast.get()); }

    void search(String q) {
        batch(() -> { lookupNotice = null; state.lookupQuery.set(q == null ? "" : q.trim()); });
    }

    /** Checks a passenger in and shows the outcome on the lookup card. */
    void checkIn(String reference) {
        try {
            Booking b = service.checkIn(reference);
            lookupNotice = "Checked in. Safe travels, " + b.passengerName() + "!";
        } catch (FidsException e) {
            lookupNotice = e.getMessage();
        }
        refresh();
    }

    // ---- staff mode -----------------------------------------------------------------------------

    void staffButtonPressed() {
        if (state.staffMode.get()) {
            batch(() -> { state.staffMode.set(false); if (state.screen.get() == Screen.ADMIN) state.screen.set(Screen.BOARD); });
            return;
        }
        PasswordField pw = new PasswordField();
        pw.setPromptText("Staff password");
        Label hint = new Label("Demo password: " + STAFF_PASSWORD);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Staff login");
        dialog.setHeaderText("Enter the staff password to manage flights");
        dialog.getDialogPane().setContent(new VBox(8, pw, hint));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (STAFF_PASSWORD.equals(pw.getText())) {
                batch(() -> { state.staffMode.set(true); state.screen.set(Screen.ADMIN); });
            } else {
                banner.show("LOGIN FAILED", "Incorrect staff password");
            }
        }
    }

    /** Runs a staff command; returns an error message, or empty on success. */
    Optional<String> runStaff(AdminCommand cmd) {
        try {
            service.execute(cmd, "STAFF");
            return Optional.empty();
        } catch (FidsException e) {
            return Optional.of(e.getMessage());
        } finally {
            refresh();
        }
    }

    Optional<String> undoStaff() {
        try {
            if (service.undoLast("STAFF").isEmpty()) return Optional.of("Nothing to undo.");
            return Optional.empty();
        } catch (FidsException e) {
            return Optional.of(e.getMessage());
        } finally {
            refresh();
        }
    }

    private void batch(Runnable r) {
        suspended++;
        try { r.run(); } finally { suspended--; }
        refresh();
    }

    // ---- observer: data changes from simulation or staff ------------------------------------------

    @Override
    public void onFlightEvent(FlightEvent e) {
        lastUpdated = LocalDateTime.now();
        flashId = e.flight().id();
        Notification.from(e)
                .filter(n -> n.isBroadcast() || n.flight().id().equals(state.pinnedFlightId.get()))
                .ifPresent(n -> banner.show(n.heading(), n.body()));
        refresh();
        flashId = null;
    }

    // ---- the one redraw method ---------------------------------------------------------------

    /** Re-renders every view from the current state + data. */
    void refresh() {
        applyDisplayModes();
        header.update(state);
        showScreen();

        List<Flight> rows = boardRows();
        Flight expanded = service.find(state.expandedFlightId.get()).filter(rows::contains).orElse(null);
        board.update(rows, state, expanded, flashId, lastUpdated);

        Flight found = null;
        String q = state.lookupQuery.get();
        if (q != null && !q.isBlank()) {
            try { found = service.lookup(q); } catch (FlightNotFoundException ignored) { /* shown as "not found" */ }
        }
        Optional<Booking> booking = found == null ? Optional.empty() : service.bookingMatching(q, found);
        List<Booking> others = booking.map(b -> service.bookingsOf(b.passenger()).stream()
                .filter(x -> x != b).toList()).orElse(List.of());
        lookup.update(state, Optional.ofNullable(found), booking, others,
                found != null && found.id().equals(state.pinnedFlightId.get()), lookupNotice);

        wayfinder.update(routeTarget(), service.flights());
        admin.update(service.flights(), service.auditLog(), service.canUndo(), simulation);
    }

    /** Pinned flight first, then the rest in the chosen order; long-departed flights are retired. */
    private List<Flight> boardRows() {
        String pinned = state.pinnedFlightId.get();
        Comparator<Flight> cmp = state.sortKey.get().comparator();
        if (!state.ascending.get()) cmp = cmp.reversed();
        cmp = cmp.thenComparing(Flight::scheduledTime);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        return service.flights().stream()
                .filter(f -> f.id().equals(pinned)
                        || !(f.status() == FlightStatus.DEPARTED && f.estimatedTime().isBefore(cutoff)))
                .sorted(Comparator.comparing((Flight f) -> !f.id().equals(pinned)).thenComparing(cmp))
                .toList();
    }

    /** Selected flight drives the map; falls back to the pinned flight. */
    private Flight routeTarget() {
        return service.find(state.selectedFlightId.get())
                .or(() -> service.find(state.pinnedFlightId.get())).orElse(null);
    }

    private void applyDisplayModes() {
        toggleClass("high-contrast", state.highContrast.get());
        toggleClass("large-text", state.largeText.get());
    }

    private void toggleClass(String name, boolean on) {
        root.getStyleClass().remove(name);
        if (on) root.getStyleClass().add(name);
    }

    private void showScreen() {
        Screen s = state.screen.get();
        if (s == Screen.ADMIN && !state.staffMode.get()) s = Screen.BOARD;
        if (s == shownScreen) return;
        boolean first = shownScreen == null;
        shownScreen = s;
        Screen visible = s;
        screens.forEach((k, n) -> { n.setVisible(k == visible); n.setManaged(k == visible); });
        if (!first) {
            FadeTransition ft = new FadeTransition(Duration.millis(220), screens.get(s));
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        }
    }
}
