package com.example.airplane.service;

import com.example.airplane.exception.FidsException;
import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightStatus;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Drives the "live" board: every ~12s it changes one flight's gate or status. All changes go through
 * {@link FlightService}, so they obey the same rules as staff actions and notify the same listeners.
 */
public class SimulationService {
    private final FlightService service;     // the business layer we change flights through
    private final Random rnd = new Random(); // source of random choices
    private final Timeline timeline;         // JavaFX timer that calls tick() repeatedly
    // Current strategy (declared as the interface type so it can be swapped; default is demo mode)
    private StatusTransitionStrategy strategy = new RandomProgressionStrategy();

    public SimulationService(FlightService service) {
        this.service = service;
        // Every 12 seconds run tick(); "e -> tick()" is a lambda used as an event handler
        this.timeline = new Timeline(new KeyFrame(Duration.seconds(12), e -> tick()));
        timeline.setCycleCount(Timeline.INDEFINITE); // repeat forever
    }

    // Start the timer.
    public void start() { timeline.play(); }
    // Stop the timer completely.
    public void stop() { timeline.stop(); }
    // Pause or resume the timer.
    public void setRunning(boolean running) { if (running) timeline.play(); else timeline.pause(); }
    // True while the timer is running.
    public boolean isRunning() { return timeline.getStatus() == javafx.animation.Animation.Status.RUNNING; }
    // Getter and setter for the strategy (this is how the Strategy pattern is switched at run time).
    public StatusTransitionStrategy strategy() { return strategy; }
    public void setStrategy(StatusTransitionStrategy strategy) { this.strategy = strategy; }

    /** One simulation step: usually a status change, sometimes a gate change. */
    public void tick() {
        // 1 in 4 chance of trying a gate change first; if it worked, this step is finished
        if (rnd.nextInt(4) == 0 && triggerGateChange()) return;
        // Collect flights that are still active (not departed/cancelled) and shuffle them for randomness
        List<Flight> active = new ArrayList<>(service.flights().stream().filter(f -> f.status().isActive()).toList());
        Collections.shuffle(active, rnd);
        LocalDateTime now = LocalDateTime.now();
        for (Flight f : active) {
            // Ask the strategy what should happen to this flight
            Optional<FlightStatus> next = strategy.next(f, now, rnd);
            if (next.isEmpty()) continue; // nothing to do for this flight, try the next one
            try {
                // A delay is a special case: it moves the time by 25 minutes
                if (next.get() == FlightStatus.DELAYED) service.delay(f, 25);
                else service.moveStatus(f, next.get());
                service.record("SIMULATION", f.displayNumber() + " is now " + next.get().label()); // add to audit log
                return; // only one change per tick
            } catch (FidsException e) {
                // Rule rejected this step; try another flight.
            }
        }
    }

    /** Moves a random eligible flight to a free gate on its own pier. Returns false if none could move. */
    public boolean triggerGateChange() {
        // Flights that are still allowed to change gate (method reference as a filter)
        List<Flight> candidates = service.flights().stream().filter(Flight::canChangeGate).toList();
        if (candidates.isEmpty()) return false;
        // Pick one at random
        Flight f = candidates.get(rnd.nextInt(candidates.size()));
        // Try up to 10 random gates (1 to 8) on the same pier
        for (int i = 0; i < 10; i++) {
            String gate = f.pier() + String.valueOf(1 + rnd.nextInt(8));
            try {
                String old = service.changeGate(f, gate); // the service checks the rules
                service.record("SIMULATION", f.displayNumber() + " gate " + old + " -> " + gate);
                return true;
            } catch (FidsException e) {
                // Gate occupied or unchanged; try another.
            }
        }
        return false;
    }
}
