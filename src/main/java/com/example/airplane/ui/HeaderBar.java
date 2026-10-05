package com.example.airplane.ui;

import com.example.airplane.state.AppState;
import com.example.airplane.state.AppState.Screen;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * Top bar: branding, screen navigation, accessibility toggles, staff login and live clock.
 * Inheritance: it IS an HBox (a row layout). It never changes data itself; it calls the controller.
 */
class HeaderBar extends HBox {
    // Time text (e.g. 14:05:09), updated every second.
    private final Label clock = Ui.label("", "clock");
    // Date text under the clock.
    private final Label date = Ui.label("", "clock-date");
    // Maps each Screen (enum) to its navigation tab; EnumMap is a fast Map for enum keys.
    private final Map<Screen, ToggleButton> tabs = new EnumMap<>(Screen.class);
    // Staff login/logout button; its text changes with the state.
    private final Button staffBtn = new Button();
    // On/off buttons for accessibility (larger text, high contrast).
    private final ToggleButton largeTextBtn = new ToggleButton("Aa");
    private final ToggleButton contrastBtn = new ToggleButton("HC");

    /** Builds the bar. It receives the controller so button clicks can be passed on to it. */
    HeaderBar(FidsController ctl) {
        super(16); // 16px gap between items
        getStyleClass().add("header-bar");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 18, 10, 18)); // top, right, bottom, left

        // Branding: two text lines stacked vertically...
        VBox brand = new VBox(0, Ui.label("SKYSCOPE", "brand-title"),
                Ui.label("NAIROBI  ·  NBO  ·  DEPARTURES", "brand-sub"));
        // ...placed next to a take-off icon.
        HBox logo = new HBox(14, Ui.icon("mdi2a-airplane-takeoff", 34, "brand-icon"), brand);
        logo.setAlignment(Pos.CENTER_LEFT);

        // A ToggleGroup makes sure only one tab is selected at a time.
        ToggleGroup group = new ToggleGroup();
        HBox nav = new HBox(6);
        nav.setAlignment(Pos.CENTER);
        // One tab per screen: label, icon and a tooltip showing the keyboard shortcut.
        addTab(nav, group, ctl, Screen.BOARD, "Departures", "mdi2v-view-list", "Ctrl+1");
        addTab(nav, group, ctl, Screen.LOOKUP, "My Flight", "mdi2m-magnify", "Ctrl+2 or Ctrl+F");
        addTab(nav, group, ctl, Screen.MAP, "Wayfinding", "mdi2d-directions", "Ctrl+3");
        addTab(nav, group, ctl, Screen.ADMIN, "Staff Panel", "mdi2a-account-circle", "Ctrl+4 (staff only)");

        // An empty Region that grows to fill spare space, pushing the items after it to the right edge.
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        largeTextBtn.setTooltip(new Tooltip("Larger text")); // hover hint
        largeTextBtn.setFocusTraversable(false);
        largeTextBtn.setOnAction(e -> ctl.toggleLargeText()); // lambda event handler: click -> tell controller
        largeTextBtn.setAccessibleText("Toggle larger text"); // read out by screen readers
        contrastBtn.setTooltip(new Tooltip("High contrast"));
        contrastBtn.setFocusTraversable(false);
        contrastBtn.setOnAction(e -> ctl.toggleHighContrast());
        contrastBtn.setAccessibleText("Toggle high contrast");
        staffBtn.setFocusTraversable(false);
        staffBtn.setOnAction(e -> ctl.staffButtonPressed()); // opens the login dialog or logs out

        // Clock and date stacked, aligned to the right.
        VBox time = new VBox(0, clock, date);
        time.setAlignment(Pos.CENTER_RIGHT);

        // Add everything to this bar from left to right.
        getChildren().addAll(logo, nav, spacer, largeTextBtn, contrastBtn, staffBtn, time);
        tickClock(); // show the current time immediately
    }

    /** Creates one navigation tab and adds it to the nav row (helper to avoid repeating code four times). */
    private void addTab(HBox nav, ToggleGroup g, FidsController ctl, Screen s, String text, String icon, String hint) {
        ToggleButton b = new ToggleButton(text, Ui.icon(icon, 16));
        b.setToggleGroup(g);
        b.getStyleClass().add("nav-tab");
        b.setFocusTraversable(false);
        b.setTooltip(new Tooltip(hint));
        // Clicking the selected tab must not deselect it.
        b.setOnAction(e -> { b.setSelected(true); ctl.showScreen(s); });
        tabs.put(s, b); // remember the tab so update() can find it later
        nav.getChildren().add(b);
    }

    /** Refreshes the clock and date labels with the current time (called every second by the controller). */
    void tickClock() {
        LocalDateTime now = LocalDateTime.now();
        clock.setText(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        // e.g. "MONDAY, 5 OCTOBER 2026"
        date.setText(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")).toUpperCase());
    }

    /** Makes the bar match the current AppState (which tab is selected, staff mode, toggles). */
    void update(AppState state) {
        boolean staff = state.staffMode.get();
        // The Staff Panel tab is only visible when logged in as staff.
        ToggleButton admin = tabs.get(Screen.ADMIN);
        admin.setVisible(staff);
        admin.setManaged(staff); // managed=false means it also takes no space in the layout
        tabs.get(state.screen.get()).setSelected(true); // highlight the current screen's tab
        staffBtn.setText(staff ? "Staff Logout" : "Staff Login");
        largeTextBtn.setSelected(state.largeText.get());
        contrastBtn.setSelected(state.highContrast.get());
    }
}
