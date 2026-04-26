package com.kolhey.p2p.gui.utils;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Animation utilities for smooth transitions and effects
 */
public class AnimationUtils {

    /**
     * Create a fade-in animation
     */
    public static FadeTransition fadeIn(Node node) {
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(300), node);
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);
        return fadeTransition;
    }

    /**
     * Create a fade-out animation
     */
    public static FadeTransition fadeOut(Node node) {
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(300), node);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.0);
        return fadeTransition;
    }

    /**
     * Create a scale-up animation
     */
    public static ScaleTransition scaleUp(Node node) {
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(300), node);
        scaleTransition.setFromX(0.8);
        scaleTransition.setFromY(0.8);
        scaleTransition.setToX(1.0);
        scaleTransition.setToY(1.0);
        return scaleTransition;
    }

    /**
     * Create a slide-in animation from left
     */
    public static TranslateTransition slideInFromLeft(Node node) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), node);
        transition.setFromX(-100);
        transition.setToX(0);
        return transition;
    }

    /**
     * Create a slide-out animation to right
     */
    public static TranslateTransition slideOutToRight(Node node) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), node);
        transition.setFromX(0);
        transition.setToX(100);
        return transition;
    }

    /**
     * Create a bounce animation
     */
    public static ScaleTransition bounce(Node node) {
        ScaleTransition bounce = new ScaleTransition(Duration.millis(100), node);
        bounce.setFromX(1.0);
        bounce.setFromY(1.0);
        bounce.setToX(1.1);
        bounce.setToY(1.1);

        ScaleTransition bounceback = new ScaleTransition(Duration.millis(100), node);
        bounceback.setFromX(1.1);
        bounceback.setFromY(1.1);
        bounceback.setToX(1.0);
        bounceback.setToY(1.0);

        bounce.setOnFinished(event -> bounceback.play());

        return bounce;
    }

    /**
     * Create a pulse animation for status indicators
     */
    public static ScaleTransition pulse(Node node) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(600), node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.2);
        pulse.setToY(1.2);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(ScaleTransition.INDEFINITE);
        return pulse;
    }

    /**
     * Create a slide-in notification animation from top
     */
    public static TranslateTransition slideInNotification(Node node) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(400), node);
        transition.setFromY(-50);
        transition.setToY(0);
        return transition;
    }

    /**
     * Create a slide-out notification animation to top with fade
     */
    public static SequentialTransition slideOutNotification(Node node) {
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), node);
        slideOut.setFromY(0);
        slideOut.setToY(-50);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), node);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        SequentialTransition sequence = new SequentialTransition(slideOut, fadeOut);
        return sequence;
    }

    /**
     * Create a fade-in scale-up animation for new connection cards
     */
    public static SequentialTransition fadeInScaleUp(Node node) {
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), node);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(200), node);
        scaleUp.setFromX(0.8);
        scaleUp.setFromY(0.8);
        scaleUp.setToX(1.0);
        scaleUp.setToY(1.0);

        SequentialTransition sequence = new SequentialTransition();
        sequence.getChildren().addAll(fadeIn, scaleUp);
        return sequence;
    }
}
