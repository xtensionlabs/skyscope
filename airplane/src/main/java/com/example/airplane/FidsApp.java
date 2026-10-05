package com.example.airplane;

import com.example.airplane.ui.FidsController;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Application entry point for the Airport Flight Information Display System.
 * Inheritance: extends JavaFX's Application class, so JavaFX knows how to start our program.
 */
public class FidsApp extends Application {
    // The controller (presenter) that builds all the screens and handles user actions.
    private FidsController controller;

    /** Called by JavaFX after launch(); builds the window (Stage) and shows it. */
    @Override // Polymorphism: we replace Application's empty start() with our own version
    public void start(Stage stage) {
        controller = new FidsController();
        // Size the window from the usable screen area so nothing is cut off on scaled (HiDPI) displays.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        // Width/height = 94%/92% of the screen, but never bigger than 1280 x 780.
        double w = Math.min(1280, screen.getWidth() * 0.94);
        double h = Math.min(780, screen.getHeight() * 0.92);
        // A Scene holds everything shown in the window; its root is the controller's main layout.
        Scene scene = new Scene(controller.root(), w, h);
        // Let the controller attach CSS, keyboard shortcuts, the simulation and the clock.
        controller.start(scene);
        stage.setTitle("NBO Departures – Flight Information Display");
        // Minimum window size so the layout cannot be squashed too small.
        stage.setMinWidth(Math.min(900, w));
        stage.setMinHeight(Math.min(560, h));
        stage.setScene(scene);
        // Centre the window on the screen.
        stage.setX(screen.getMinX() + (screen.getWidth() - w) / 2);
        stage.setY(screen.getMinY() + (screen.getHeight() - h) / 2);
        stage.show();
    }

    /** Called by JavaFX when the app closes; lets the controller stop the simulation and save data. */
    @Override
    public void stop() {
        if (controller != null) controller.stop();
    }

    /** Normal Java main method; launch() starts JavaFX, which then calls start(). */
    public static void main(String[] args) {
        launch(args);
    }
}
