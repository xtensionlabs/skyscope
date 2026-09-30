package com.example.airplane.ui;

import com.example.airplane.state.AppState;
import com.example.airplane.state.AppState.Screen;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/** Top bar: branding, screen navigation, live clock and theme toggle. */
class HeaderBar extends HBox {
    private final Label clock = Ui.label("", "clock");
    private final Label date = Ui.label("", "clock-date");
    private final Map<Screen, ToggleButton> tabs = new EnumMap<>(Screen.class);

    HeaderBar(FidsController ctl) {
        super(16);
        getStyleClass().add("header-bar");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 18, 10, 18));

        VBox brand = new VBox(0, Ui.label("NAIROBI  ·  NBO", "brand-title"),
                Ui.label("DEPARTURES INFORMATION", "brand-sub"));
        HBox logo = new HBox(14, Ui.icon("mdi2a-airplane-takeoff", 34, "brand-icon"), brand);
        logo.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup group = new ToggleGroup();
        HBox nav = new HBox(6);
        nav.setAlignment(Pos.CENTER);
        addTab(nav, group, ctl, Screen.BOARD, "Departures", "mdi2v-view-list");
        addTab(nav, group, ctl, Screen.LOOKUP, "My Flight", "mdi2m-magnify");
        addTab(nav, group, ctl, Screen.MAP, "Wayfinding", "mdi2d-directions");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox time = new VBox(0, clock, date);
        time.setAlignment(Pos.CENTER_RIGHT);


        getChildren().addAll(logo, nav, spacer, time);
        tickClock();
    }

    private void addTab(HBox nav, ToggleGroup g, FidsController ctl, Screen s, String text, String icon) {
        ToggleButton b = new ToggleButton(text, Ui.icon(icon, 16));
        b.setToggleGroup(g);
        b.getStyleClass().add("nav-tab");
        b.setFocusTraversable(false);
        // Clicking the selected tab must not deselect it.
        b.setOnAction(e -> { b.setSelected(true); ctl.showScreen(s); });
        tabs.put(s, b);
        nav.getChildren().add(b);
    }

    void tickClock() {
        LocalDateTime now = LocalDateTime.now();
        clock.setText(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        date.setText(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")).toUpperCase());
    }

    void update(AppState state) {
        tabs.get(state.screen.get()).setSelected(true);
    }
}
