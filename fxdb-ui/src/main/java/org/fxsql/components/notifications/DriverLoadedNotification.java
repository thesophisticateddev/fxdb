package org.fxsql.components.notifications;

import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;

public class DriverLoadedNotification extends BaseNotification {
    public DriverLoadedNotification(String message) {
        super(message, Feather.ALERT_OCTAGON);
        this.getStyleClass().add(Styles.SUCCESS);
    }

    @Override
    public void show(VBox panel) {
        if (panel != null) {
            this.setOnClose(event -> {
                Animations.fadeOut(this, Duration.millis(300)).stop();
                panel.getChildren().remove(this);
            });
            panel.getChildren().add(this);
        }
    }
}
