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
    private final FlightService service;
    private final Random rnd = new Random();
    private final Timeline timeline;
    private StatusTransitionStrategy strategy = new RandomProgressionStrategy();

    public SimulationService(FlightService service) {
        this.service = service;
        this.timeline = new Timeline(new KeyFrame(Duration.seconds(12), e -> tick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void start() { timeline.play(); }
    public void stop() { timeline.stop(); }
    public void setRunning(boolean running) { if (running) timeline.play(); else timeline.pause(); }
    public boolean isRunning() { return timeline.getStatus() == javafx.animation.Animation.Status.RUNNING; }
    public StatusTransitionStrategy strategy() { return strategy; }
    public void setStrategy(StatusTransitionStrategy strategy) { this.strategy = strategy; }

    /** One simulation step: usually a status change, sometimes a gate change. */
    public void tick() {
        if (rnd.nextInt(4) == 0 && triggerGateChange()) return;
        List<Flight> active = new ArrayList<>(service.flights().stream().filter(f -> f.status().isActive()).toList());
        Collections.shuffle(active, rnd);
        LocalDateTime now = LocalDateTime.now();
        for (Flight f : active) {
            Optional<FlightStatus> next = strategy.next(f, now, rnd);
            if (next.isEmpty()) continue;
            try {
                if (next.get() == FlightStatus.DELAYED) service.delay(f, 25);
                else service.moveStatus(f, next.get());
                service.record("SIMULATION", f.displayNumber() + " is now " + next.get().label());
                return;
            } catch (FidsException e) {
                // Rule rejected this step; try another flight.
            }
        }
    }

    /** Moves a random eligible flight to a free gate on its own pier. Returns false if none could move. */
    public boolean triggerGateChange() {
        List<Flight> candidates = service.flights().stream().filter(Flight::canChangeGate).toList();
        if (candidates.isEmpty()) return false;
        Flight f = candidates.get(rnd.nextInt(candidates.size()));
        for (int i = 0; i < 10; i++) {
            String gate = f.pier() + String.valueOf(1 + rnd.nextInt(8));
            try {
                String old = service.changeGate(f, gate);
                service.record("SIMULATION", f.displayNumber() + " gate " + old + " -> " + gate);
                return true;
            } catch (FidsException e) {
                // Gate occupied or unchanged; try another.
            }
        }
        return false;
    }
}
