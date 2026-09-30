package com.example.airplane.state;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.*;

/** Central application state. Any change notifies the controller, which calls its single refresh(). */
public final class AppState {
    public enum SortKey { TIME, GATE, STATUS }
    public enum Theme { LIGHT }
    public enum Screen { BOARD, LOOKUP, MAP }

    public final ObjectProperty<SortKey> sortKey = new SimpleObjectProperty<>(SortKey.TIME);
    public final BooleanProperty ascending = new SimpleBooleanProperty(true);
    public final StringProperty pinnedFlightId = new SimpleStringProperty();
    public final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);
    public final StringProperty expandedFlightId = new SimpleStringProperty();
    public final StringProperty selectedFlightId = new SimpleStringProperty();
    public final ObjectProperty<Screen> screen = new SimpleObjectProperty<>(Screen.BOARD);
    public final StringProperty lookupQuery = new SimpleStringProperty("");

    /** Runs the callback whenever any state variable changes. */
    public void onChange(Runnable r) {
        InvalidationListener l = (Observable o) -> r.run();
        for (Observable o : new Observable[]{sortKey, ascending, pinnedFlightId, theme, expandedFlightId,
                selectedFlightId, screen, lookupQuery}) {
            o.addListener(l);
        }
    }
}
