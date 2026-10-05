package com.example.airplane.ui;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Floating announcement pill that slides in from the top and auto-dismisses.
 * Inheritance: a BannerView IS an HBox (a horizontal layout), so it can be placed on screen like any node.
 */
class BannerView extends HBox {
    // Bold heading line of the banner (text is filled in by show()).
    private final javafx.scene.control.Label title = Ui.label("", "banner-title");
    // Detail line under the heading.
    private final javafx.scene.control.Label message = Ui.label("", "banner-message");
    // Holds the whole animation (slide in, wait, slide out) so we can stop/restart it.
    private final SequentialTransition anim = new SequentialTransition();

    /** Builds the banner's layout once; it starts hidden. */
    BannerView() {
        super(14); // call HBox constructor: 14px gap between children
        getStyleClass().add("banner"); // CSS class for the pill look
        setAlignment(Pos.CENTER_LEFT);
        // Children: a warning icon, then a VBox stacking the title above the message.
        getChildren().addAll(Ui.icon("mdi2a-alert", 26, "banner-icon"), new VBox(2, title, message));
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE); // do not stretch; stay as small as its content
        setMouseTransparent(true); // clicks pass through to whatever is underneath
        setOpacity(0);   // fully transparent
        setVisible(false); // and hidden until show() is called
    }

    /** Displays the banner with the given heading and text, then hides it after a few seconds. */
    void show(String heading, String text) {
        anim.stop(); // cancel any banner animation still running
        title.setText(heading);
        message.setText(text);
        setVisible(true);
        // Slide down from 90px above to its normal place, easing out (slows at the end).
        TranslateTransition in = new TranslateTransition(Duration.millis(380), this);
        in.setFromY(-90);
        in.setToY(0);
        in.setInterpolator(Interpolator.EASE_OUT);
        // Fade in at the same time.
        FadeTransition fadeIn = new FadeTransition(Duration.millis(380), this);
        fadeIn.setToValue(1);
        // Slide back up out of view, easing in (speeds up).
        TranslateTransition out = new TranslateTransition(Duration.millis(380), this);
        out.setToY(-90);
        out.setInterpolator(Interpolator.EASE_IN);
        // Fade out at the same time.
        FadeTransition fadeOut = new FadeTransition(Duration.millis(380), this);
        fadeOut.setToValue(0);
        // Order: (slide+fade in) -> wait 6 seconds -> (slide+fade out). Parallel = run together.
        anim.getChildren().setAll(new ParallelTransition(in, fadeIn), new PauseTransition(Duration.seconds(6)),
                new ParallelTransition(out, fadeOut));
        // Lambda event handler: when everything finishes, hide the banner again.
        anim.setOnFinished(e -> setVisible(false));
        anim.playFromStart();
    }
}
