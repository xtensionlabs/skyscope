package com.example.airplane.state;

import com.example.airplane.model.Flight;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.*;

import java.util.Comparator;

/**
 * Central application state. Any change notifies the controller, which calls its single refresh().
 * Each field is a JavaFX Property, an observable value (Observer pattern built into JavaFX).
 */
public final class AppState {
    /** Sortable board columns; each one carries its own comparator (Strategy). */
    public enum SortKey {
        // Each constant is created with a different way of comparing two flights (a method reference or lambda)
        TIME(Comparator.comparing(Flight::scheduledTime)),
        GATE(Comparator.comparing(Flight::gateSortKey)),
        STATUS(Comparator.comparing(f -> f.status().ordinal())); // ordinal = position in the status enum

        private final Comparator<Flight> comparator; // how this column sorts

        SortKey(Comparator<Flight> comparator) { this.comparator = comparator; }

        // Getter used by the board when sorting.
        public Comparator<Flight> comparator() { return comparator; }
    }

    // Available colour themes (only one for now) and the screens the user can switch between.
    public enum Theme { LIGHT }
    public enum Screen { BOARD, LOOKUP, MAP, ADMIN }

    // The state variables, with their starting values:
    public final ObjectProperty<SortKey> sortKey = new SimpleObjectProperty<>(SortKey.TIME); // column used for sorting
    public final BooleanProperty ascending = new SimpleBooleanProperty(true);               // sort direction
    public final StringProperty pinnedFlightId = new SimpleStringProperty();                // flight the user pinned
    public final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);    // colour theme
    public final StringProperty expandedFlightId = new SimpleStringProperty();              // row opened for details
    public final StringProperty selectedFlightId = new SimpleStringProperty();              // currently selected row
    public final ObjectProperty<Screen> screen = new SimpleObjectProperty<>(Screen.BOARD); // screen being shown
    public final StringProperty lookupQuery = new SimpleStringProperty("");                 // text typed in lookup
    public final BooleanProperty staffMode = new SimpleBooleanProperty(false);              // staff (admin) logged in?
    public final BooleanProperty highContrast = new SimpleBooleanProperty(false);           // accessibility option
    public final BooleanProperty largeText = new SimpleBooleanProperty(false);              // accessibility option

    /** Runs the callback whenever any state variable changes. */
    public void onChange(Runnable r) {
        // One listener (a lambda) that simply runs the callback
        InvalidationListener l = (Observable o) -> r.run();
        // Attach the same listener to every state variable
        for (Observable o : new Observable[]{sortKey, ascending, pinnedFlightId, theme, expandedFlightId,
                selectedFlightId, screen, lookupQuery, staffMode, highContrast, largeText}) {
            o.addListener(l);
        }
    }
}
