package org.fxsql.components.notifications;

import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;

public class DriverLoadedNotification extends BaseNotification {
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
        }
    }
}
