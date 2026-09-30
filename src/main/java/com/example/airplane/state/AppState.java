package com.example.airplane.state;

import com.example.airplane.model.Flight;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.*;

import java.util.Comparator;

/** Central application state. Any change notifies the controller, which calls its single refresh(). */
public final class AppState {
    /** Sortable board columns; each one carries its own comparator (Strategy). */
    public enum SortKey {
        TIME(Comparator.comparing(Flight::scheduledTime)),
        GATE(Comparator.comparing(Flight::gateSortKey)),
        STATUS(Comparator.comparing(f -> f.status().ordinal()));

        private final Comparator<Flight> comparator;

        SortKey(Comparator<Flight> comparator) { this.comparator = comparator; }

        public Comparator<Flight> comparator() { return comparator; }
    }

    public enum Theme { LIGHT }
    public enum Screen { BOARD, LOOKUP, MAP, ADMIN }

    public final ObjectProperty<SortKey> sortKey = new SimpleObjectProperty<>(SortKey.TIME);
    public final BooleanProperty ascending = new SimpleBooleanProperty(true);
    public final StringProperty pinnedFlightId = new SimpleStringProperty();
    public final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);
    public final StringProperty expandedFlightId = new SimpleStringProperty();
    public final StringProperty selectedFlightId = new SimpleStringProperty();
    public final ObjectProperty<Screen> screen = new SimpleObjectProperty<>(Screen.BOARD);
    public final StringProperty lookupQuery = new SimpleStringProperty("");
    public final BooleanProperty staffMode = new SimpleBooleanProperty(false);
    public final BooleanProperty highContrast = new SimpleBooleanProperty(false);
    public final BooleanProperty largeText = new SimpleBooleanProperty(false);

    /** Runs the callback whenever any state variable changes. */
    public void onChange(Runnable r) {
        InvalidationListener l = (Observable o) -> r.run();
        for (Observable o : new Observable[]{sortKey, ascending, pinnedFlightId, theme, expandedFlightId,
                selectedFlightId, screen, lookupQuery, staffMode, highContrast, largeText}) {
            o.addListener(l);
        }
    }
}
