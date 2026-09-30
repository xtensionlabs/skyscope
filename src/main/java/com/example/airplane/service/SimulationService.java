package com.example.airplane.service;

import com.example.airplane.model.Flight;
import com.example.airplane.model.FlightRepository;
import com.example.airplane.model.FlightStatus;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/** Drives the "live" board: every ~12s a random flight changes status, occasionally its gate. */
public class SimulationService {
    public record Change(Flight flight, String oldGate, boolean gateChanged) { }

    private final FlightRepository repo;
    private final Consumer<Change> listener;
    private final Random rnd = new Random();
    private final Timeline timeline;

    public SimulationService(FlightRepository repo, Consumer<Change> listener) {
        this.repo = repo;
        this.listener = listener;
        this.timeline = new Timeline(new KeyFrame(Duration.seconds(12), e -> tick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void start() { timeline.play(); }
    public void stop() { timeline.stop(); }

    private void tick() {
        List<Flight> active = repo.flights().stream().filter(f -> f.status().isActive()).toList();
        if (active.isEmpty()) return;
        Flight f = active.get(rnd.nextInt(active.size()));
        if (rnd.nextInt(4) == 0 && f.status() != FlightStatus.GATE_CLOSED) {
            String old = f.gate();
            String pier = old.substring(0, 1);
            String next;
            do { next = pier + (1 + rnd.nextInt(8)); } while (next.equals(old));
            f.setGate(next);
            listener.accept(new Change(f, old, true));
            return;
        }
        f.setStatus(switch (f.status()) {
            case ON_TIME -> rnd.nextInt(5) == 0 ? FlightStatus.DELAYED : FlightStatus.BOARDING;
            case DELAYED -> FlightStatus.BOARDING;
            case BOARDING -> FlightStatus.GATE_CLOSED;
            default -> FlightStatus.DEPARTED;
        });
        if (f.status() == FlightStatus.DELAYED) f.setEstimatedTime(f.scheduledTime().plusMinutes(25));
        listener.accept(new Change(f, f.gate(), false));
    }
}
