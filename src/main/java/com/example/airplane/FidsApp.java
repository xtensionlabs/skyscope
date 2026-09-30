package com.example.airplane;

import com.example.airplane.ui.FidsController;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Application entry point for the Airport Flight Information Display System. */
public class FidsApp extends Application {
    private FidsController controller;

    @Override
    public void start(Stage stage) {
        controller = new FidsController();
        // Size the window from the usable screen area so nothing is cut off on scaled (HiDPI) displays.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double w = Math.min(1280, screen.getWidth() * 0.94);
        double h = Math.min(780, screen.getHeight() * 0.92);
        Scene scene = new Scene(controller.root(), w, h);
        controller.start(scene);
        stage.setTitle("NBO Departures – Flight Information Display");
        stage.setMinWidth(Math.min(900, w));
        stage.setMinHeight(Math.min(560, h));
        stage.setScene(scene);
        stage.setX(screen.getMinX() + (screen.getWidth() - w) / 2);
        stage.setY(screen.getMinY() + (screen.getHeight() - h) / 2);
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) controller.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
