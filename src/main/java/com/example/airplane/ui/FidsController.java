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
 * OOP: implements the FlightListener interface (Observer pattern) so the service can notify it of changes.
 */
public class FidsController implements FlightListener {
    // The demo staff password (a constant: static final = one shared value that never changes).
    private static final String STAFF_PASSWORD = "staff123";

    // The model/logic layer: stores flights, bookings and the audit log.
    private final FlightService service = createService();
    // Holds what the user is currently doing (screen shown, sort order, search text, etc.).
    private final AppState state = new AppState();
    // Background simulator that randomly changes flight statuses.
    private final SimulationService simulation = new SimulationService(service);
    // Two interchangeable ways of changing statuses (Strategy pattern); typed by the interface.
    private final List<StatusTransitionStrategy> strategies =
            List.of(new TimeAwareStrategy(), new RandomProgressionStrategy());

    // The views (the screens). "this" is passed so each view can call back into this controller.
    private final HeaderBar header = new HeaderBar(this);
    private final BoardView board = new BoardView(this);
    private final LookupView lookup = new LookupView(this);
    private final WayfinderView wayfinder = new WayfinderView(this);
    private final AdminView admin = new AdminView(this, strategies);
    private final BannerView banner = new BannerView();
    // Lets us find a screen's node from its Screen enum value.
    private final Map<Screen, Node> screens = new EnumMap<>(Screen.class);
    // The top-level layout of the whole window.
    private final StackPane root = new StackPane();

    // The window's Scene (set in start()).
    private Scene scene;
    // The screen currently displayed (used to avoid redoing the switch animation).
    private Screen shownScreen;
    // Id of the flight that just changed, so the board can flash its row.
    private String flashId;
    // Message shown on the lookup screen after a check-in (success or error).
    private String lookupNotice;
    // When the data last changed (shown on the board).
    private LocalDateTime lastUpdated = LocalDateTime.now();
    // Counter used by batch(): while above 0, state changes do not trigger refresh().
    private int suspended;

    /** Constructor: assembles the layout and connects the listeners. */
    public FidsController() {
        // Register each view under its Screen name.
        screens.put(Screen.BOARD, board);
        screens.put(Screen.LOOKUP, lookup);
        screens.put(Screen.MAP, wayfinder);
        screens.put(Screen.ADMIN, admin);
        // StackPane piles all screens on top of each other; showScreen() makes only one visible.
        StackPane content = new StackPane(board, lookup, wayfinder, admin);
        // BorderPane: the screens in the centre, the header bar on top.
        BorderPane layout = new BorderPane(content);
        layout.setTop(header);
        layout.getStyleClass().add("app-root");
        // Place the banner at the top centre, just below the header (74px margin from the top).
        StackPane.setAlignment(banner, Pos.TOP_CENTER);
        StackPane.setMargin(banner, new Insets(74, 0, 0, 0));
        // Banner is added last so it floats over the layout.
        root.getChildren().addAll(layout, banner);

        simulation.setStrategy(strategies.get(0)); // start with the realistic (clock-based) strategy
        service.addListener(this); // subscribe: onFlightEvent() is called whenever a flight changes
        // Whenever any AppState value changes, redraw (unless a batch is in progress).
        state.onChange(() -> { if (suspended == 0) refresh(); });
    }

    /** Saved JSON data if possible; otherwise fall back to the in-memory sample data. */
    private static FlightService createService() {
        try {
            return new FlightService(new JsonDataSource(Path.of("data", "fids.json")));
        } catch (IOException | RuntimeException e) { // exception handling: catch either error type
            System.err.println("Using in-memory sample data (" + e.getMessage() + ")");
            try {
                return new FlightService(new FlightRepository());
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    /** Getter used by FidsApp to put this layout into the Scene. */
    public StackPane root() { return root; }

    /** Finishes setup once the Scene exists: CSS, shortcuts, simulation and the one-second clock. */
    public void start(Scene scene) {
        this.scene = scene;
        // Load the stylesheets (base look + light theme).
        scene.getStylesheets().setAll(
                Objects.requireNonNull(getClass().getResource("/css/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/light.css")).toExternalForm());
        // Keyboard shortcuts: each lambda runs when its key combination is pressed.
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+F"), () -> { showScreen(Screen.LOOKUP); focusLookup(); });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+1"), () -> { showScreen(Screen.BOARD); Platform.runLater(board::focusTable); });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+2"), () -> { showScreen(Screen.LOOKUP); focusLookup(); });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+3"), () -> showScreen(Screen.MAP));
        // The staff screen is only reachable when staff mode is on.
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+4"), () -> { if (state.staffMode.get()) showScreen(Screen.ADMIN); });
        refresh(); // draw everything for the first time
        simulation.start(); // begin changing flight statuses in the background
        // One-second clock drives the header and countdown; every 30s we also refresh to retire old flights.
        int[] ticks = {0}; // array so the lambda below can modify the counter
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            header.tickClock();
            lookup.tick();
            if (++ticks[0] % 30 == 0) refresh();
        }));
        clock.setCycleCount(Timeline.INDEFINITE); // repeat forever
        clock.play();
    }

    /** Called when the app closes: stop the simulation and save the data. */
    public void stop() {
        simulation.stop();
        service.persist();
    }

    // Focuses the search box; runLater waits until the screen is actually visible.
    private void focusLookup() { Platform.runLater(lookup::focusSearch); }

    // Gates free around a departure time (ignoring one flight, e.g. the one being moved); used by the staff drop-downs.
    List<String> freeGates(java.time.LocalDateTime when, Flight ignore) { return service.availableGates(when, ignore); }

    // Lets the admin view access the simulation (package-private getter).
    SimulationService simulation() { return simulation; }

    // ---- user actions (all mutate state; state changes trigger refresh) ----------------------

    /** Sorts the board by a column; clicking the same column again reverses the order. */
    void sortBy(SortKey key) {
        batch(() -> { // batch: change several values but redraw only once
            if (state.sortKey.get() == key) state.ascending.set(!state.ascending.get());
            else { state.sortKey.set(key); state.ascending.set(true); }
        });
    }

    /** Pins a flight to the top; pinning the already pinned flight un-pins it. */
    void togglePin(String id) {
        state.pinnedFlightId.set(Objects.equals(state.pinnedFlightId.get(), id) ? null : id);
    }

    /** Clicking a row expands its details (or collapses if already open) and selects it. */
    void onRowClicked(String id) {
        batch(() -> {
            state.expandedFlightId.set(Objects.equals(state.expandedFlightId.get(), id) ? null : id);
            state.selectedFlightId.set(id);
        });
    }

    /** Selects the flight and switches to the map screen. */
    void showRoute(String id) {
        batch(() -> { state.selectedFlightId.set(id); state.screen.set(Screen.MAP); });
    }

    // Small one-line actions: they just change the state, which triggers refresh().
    void selectFlight(String id) { state.selectedFlightId.set(id); }
    void showScreen(Screen s) { state.screen.set(s); }
    void toggleLargeText() { state.largeText.set(!state.largeText.get()); }
    void toggleHighContrast() { state.highContrast.set(!state.highContrast.get()); }

    /** Stores the search text (trimmed; null becomes empty) and clears any old notice. */
    void search(String q) {
        batch(() -> { lookupNotice = null; state.lookupQuery.set(q == null ? "" : q.trim()); });
    }

    /** Checks a passenger in and shows the outcome on the lookup card. */
    void checkIn(String reference) {
        try {
            Booking b = service.checkIn(reference);
            lookupNotice = "Checked in. Safe travels, " + b.passengerName() + "!";
        } catch (FidsException e) { // our own custom exception, e.g. booking not found
            lookupNotice = e.getMessage();
        }
        refresh();
    }

    // ---- staff mode -----------------------------------------------------------------------------

    /** Handles the Staff Login/Logout button. */
    void staffButtonPressed() {
        // Already logged in: this press means logout; leave the staff screen if we are on it.
        if (state.staffMode.get()) {
            batch(() -> { state.staffMode.set(false); if (state.screen.get() == Screen.ADMIN) state.screen.set(Screen.BOARD); });
            return;
        }
        // Otherwise build a login dialog with a password box and a hint label.
        PasswordField pw = new PasswordField();
        pw.setPromptText("Staff password");
        Label hint = new Label("Demo password: " + STAFF_PASSWORD);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Staff login");
        dialog.setHeaderText("Enter the staff password to manage flights");
        dialog.getDialogPane().setContent(new VBox(8, pw, hint));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // showAndWait pauses here until the user closes the dialog; Optional holds the button pressed.
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (STAFF_PASSWORD.equals(pw.getText())) { // correct password: enter staff mode
                batch(() -> { state.staffMode.set(true); state.screen.set(Screen.ADMIN); });
            } else {
                banner.show("LOGIN FAILED", "Incorrect staff password");
            }
        }
    }

    /** Runs a staff command; returns an error message, or empty on success. */
    Optional<String> runStaff(AdminCommand cmd) {
        try {
            service.execute(cmd, "STAFF"); // Command pattern: the action is passed in as an object
            return Optional.empty();
        } catch (FidsException e) {
            return Optional.of(e.getMessage());
        } finally {
            refresh(); // finally: runs whether it succeeded or failed
        }
    }

    /** Undoes the last staff command; returns an error message, or empty on success. */
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

    /** Runs several state changes but redraws only once at the end (suspended pauses auto-refresh). */
    private void batch(Runnable r) {
        suspended++;
        try { r.run(); } finally { suspended--; }
        refresh();
    }

    // ---- observer: data changes from simulation or staff ------------------------------------------

    /** Observer callback: the service calls this whenever a flight changes. */
    @Override
    public void onFlightEvent(FlightEvent e) {
        lastUpdated = LocalDateTime.now();
        flashId = e.flight().id(); // mark which row to flash
        // Show a banner if the event is for everyone, or concerns the pinned flight.
        Notification.from(e)
                .filter(n -> n.isBroadcast() || n.flight().id().equals(state.pinnedFlightId.get()))
                .ifPresent(n -> banner.show(n.heading(), n.body()));
        refresh();
        flashId = null; // clear so the flash happens only once
    }

    // ---- the one redraw method ---------------------------------------------------------------

    /** Re-renders every view from the current state + data. */
    void refresh() {
        applyDisplayModes(); // large-text / high-contrast CSS
        header.update(state);
        showScreen();

        // Board: get the sorted rows and the expanded flight (only if it is still in the rows).
        List<Flight> rows = boardRows();
        Flight expanded = service.find(state.expandedFlightId.get()).filter(rows::contains).orElse(null);
        board.update(rows, state, expanded, flashId, lastUpdated);

        // Lookup: search for the flight only when there is search text.
        Flight found = null;
        String q = state.lookupQuery.get();
        if (q != null && !q.isBlank()) {
            try { found = service.lookup(q); } catch (FlightNotFoundException ignored) { /* shown as "not found" */ }
        }
        // Find the passenger's booking for this search, and their other bookings (excluding this one).
        Optional<Booking> booking = found == null ? Optional.empty() : service.bookingMatching(q, found);
        List<Booking> others = booking.map(b -> service.bookingsOf(b.passenger()).stream()
                .filter(x -> x != b).toList()).orElse(List.of());
        lookup.update(state, Optional.ofNullable(found), booking, others,
                found != null && found.id().equals(state.pinnedFlightId.get()), lookupNotice);

        // Map and staff screens.
        wayfinder.update(routeTarget(), service.flights());
        admin.update(service.flights(), service.auditLog(), service.canUndo(), simulation);
    }

    /** Pinned flight first, then the rest in the chosen order; long-departed flights are retired. */
    private List<Flight> boardRows() {
        String pinned = state.pinnedFlightId.get();
        // Comparator = the sorting rule; reverse it for descending order, ties broken by scheduled time.
        Comparator<Flight> cmp = state.sortKey.get().comparator();
        if (!state.ascending.get()) cmp = cmp.reversed();
        cmp = cmp.thenComparing(Flight::scheduledTime);
        // Flights that departed more than 10 minutes ago are hidden.
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        // Stream: filter out old departed flights (but keep the pinned one), sort (pinned first), make a list.
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

    // Applies the accessibility modes by adding or removing CSS classes on the root.
    private void applyDisplayModes() {
        toggleClass("high-contrast", state.highContrast.get());
        toggleClass("large-text", state.largeText.get());
    }

    // Removes the class first (avoids duplicates), then adds it back if it should be on.
    private void toggleClass(String name, boolean on) {
        root.getStyleClass().remove(name);
        if (on) root.getStyleClass().add(name);
    }

    /** Shows only the screen chosen in the state (staff screen is blocked if not logged in). */
    private void showScreen() {
        Screen s = state.screen.get();
        if (s == Screen.ADMIN && !state.staffMode.get()) s = Screen.BOARD;
        if (s == shownScreen) return; // already showing it: nothing to do
        boolean first = shownScreen == null;
        shownScreen = s;
        Screen visible = s; // effectively-final copy, needed inside the lambda
        // Show the chosen screen; hide the others (managed=false so hidden ones take no space).
        screens.forEach((k, n) -> { n.setVisible(k == visible); n.setManaged(k == visible); });
        // Fade the new screen in (skipped on first display).
        if (!first) {
            FadeTransition ft = new FadeTransition(Duration.millis(220), screens.get(s));
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        }
    }
}
