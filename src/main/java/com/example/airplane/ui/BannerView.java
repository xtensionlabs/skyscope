package com.example.airplane.ui;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Floating announcement pill that slides in from the top and auto-dismisses. */
class BannerView extends HBox {
    private final javafx.scene.control.Label title = Ui.label("", "banner-title");
    private final javafx.scene.control.Label message = Ui.label("", "banner-message");
    private final SequentialTransition anim = new SequentialTransition();

    BannerView() {
        super(14);
        getStyleClass().add("banner");
        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(Ui.icon("mdi2a-alert", 26, "banner-icon"), new VBox(2, title, message));
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setMouseTransparent(true);
        setOpacity(0);
        setVisible(false);
    }

    void show(String heading, String text) {
        anim.stop();
        title.setText(heading);
        message.setText(text);
        setVisible(true);
        TranslateTransition in = new TranslateTransition(Duration.millis(380), this);
        in.setFromY(-90);
        in.setToY(0);
        in.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(380), this);
        fadeIn.setToValue(1);
        TranslateTransition out = new TranslateTransition(Duration.millis(380), this);
        out.setToY(-90);
        out.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(380), this);
        fadeOut.setToValue(0);
        anim.getChildren().setAll(new ParallelTransition(in, fadeIn), new PauseTransition(Duration.seconds(6)),
                new ParallelTransition(out, fadeOut));
        anim.setOnFinished(e -> setVisible(false));
        anim.playFromStart();
    }
}
