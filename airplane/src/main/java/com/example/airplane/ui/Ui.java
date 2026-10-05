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

/**
 * Small factory helpers shared by the views.
 * Utility class: only static methods, no objects. "final" + private constructor stop anyone creating or extending it.
 * Reuse: avoids repeating the same "create control + add CSS classes" code in every view.
 */
final class Ui {
    // Formatter that prints a time as hours:minutes, e.g. 14:05.
    static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /** Creates an icon of a given size; "styles..." (varargs) are any number of CSS class names. */
    static FontIcon icon(String code, int size, String... styles) {
        FontIcon i = new FontIcon(code);
        i.setIconSize(size);
        i.getStyleClass().addAll(styles); // CSS classes decide the colour/look
        return i;
    }

    /** Creates a text label with the given CSS style classes. */
    static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }

    /** Creates a button with text and a 16px icon. */
    static Button button(String text, String iconCode, String... styles) {
        Button b = new Button(text, icon(iconCode, 16));
        b.getStyleClass().addAll(styles);
        b.setFocusTraversable(false); // Tab key skips it; avoids an ugly focus outline
        return b;
    }

    /** Colour-coded status pill with icon. */
    static Node statusBadge(FlightStatus s) {
        // HBox lays children out in a row with 6px gap: icon then upper-case status text.
        HBox box = new HBox(6, icon(s.icon(), 14, "badge-icon"), label(s.label().toUpperCase(), "badge-text"));
        box.setAlignment(Pos.CENTER);
        // "badge-" + styleKey picks a colour per status (e.g. delayed = red) in the CSS file.
        box.getStyleClass().addAll("badge", "badge-" + s.styleKey());
        return box;
    }

    /** Simple airline "logo": a rounded chip in the brand colour with the IATA code. */
    static Node airlineChip(Airline a) {
        Label code = label(a.code(), "airline-chip-text");
        StackPane chip = new StackPane(code); // StackPane centres its child
        chip.getStyleClass().add("airline-chip");
        // Inline CSS: colour comes from the Airline object, so it can't be fixed in the stylesheet.
        chip.setStyle("-fx-background-color: " + a.color() + ";");
        return chip;
    }

    /** Returns the time left until target as HH:MM:SS (never negative). */
    static String countdown(LocalDateTime target) {
        // Seconds between now and target; Math.max(0, ...) stops negative values once it has passed.
        long s = Math.max(0, Duration.between(LocalDateTime.now(), target).getSeconds());
        // Split seconds into hours, minutes, seconds; %02d pads with zeros.
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    // Private constructor: Encapsulation - nobody can create a Ui object.
    private Ui() { }
}
