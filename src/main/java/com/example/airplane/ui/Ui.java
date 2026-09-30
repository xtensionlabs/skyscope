package com.example.airplane.ui;

import com.example.airplane.model.Airline;
import com.example.airplane.model.FlightStatus;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Small factory helpers shared by the views. */
final class Ui {
    static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    static FontIcon icon(String code, int size, String... styles) {
        FontIcon i = new FontIcon(code);
        i.setIconSize(size);
        i.getStyleClass().addAll(styles);
        return i;
    }

    static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }

    static Button button(String text, String iconCode, String... styles) {
        Button b = new Button(text, icon(iconCode, 16));
        b.getStyleClass().addAll(styles);
        b.setFocusTraversable(false);
        return b;
    }

    /** Colour-coded status pill with icon. */
    static Node statusBadge(FlightStatus s) {
        HBox box = new HBox(6, icon(s.icon(), 14, "badge-icon"), label(s.label().toUpperCase(), "badge-text"));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().addAll("badge", "badge-" + s.styleKey());
        return box;
    }

    /** Simple airline "logo": a rounded chip in the brand colour with the IATA code. */
    static Node airlineChip(Airline a) {
        Label code = label(a.code(), "airline-chip-text");
        StackPane chip = new StackPane(code);
        chip.getStyleClass().add("airline-chip");
        chip.setStyle("-fx-background-color: " + a.color() + ";");
        return chip;
    }

    static String countdown(LocalDateTime target) {
        long s = Math.max(0, Duration.between(LocalDateTime.now(), target).getSeconds());
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private Ui() { }
}
