package org.fxsql.components.notifications;

import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;

public class DriverLoadedNotification extends BaseNotification {
    private static final Duration AUTO_CLOSE_DELAY = Duration.seconds(10);

    public DriverLoadedNotification(String message) {
        super(message, Feather.ALERT_OCTAGON);
        this.setPrefHeight(Region.USE_PREF_SIZE);
        this.setMaxHeight(Region.USE_PREF_SIZE);

        this.getStyleClass().add(Styles.SUCCESS);
    }

    @Override
    public void show(StackPane panel) {
        if (panel != null) {
            this.setOnClose(event -> {
                Animations.fadeOut(this, Duration.millis(300)).stop();
                panel.getChildren().remove(this);
            });

            panel.getChildren().add(this);
            // Auto close after 10 seconds
            PauseTransition delay = new PauseTransition(AUTO_CLOSE_DELAY);
            delay.setOnFinished(e -> close(panel));
            delay.play();
        }
    }

    private void close(StackPane panel) {
        Animations.fadeOut(this, Duration.millis(300))
                .setOnFinished(e -> panel.getChildren().remove(this));
    }
}
