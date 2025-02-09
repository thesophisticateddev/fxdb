package org.fxsql.components.notifications;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class DriverDownloadNotificationComplete extends Notification {
    public DriverDownloadNotificationComplete(String msg){
        super(msg , new FontIcon(Feather.DOWNLOAD));
        this.getStyleClass().addAll(Styles.ELEVATED_1, Styles.SUCCESS);


    }

    public void show(VBox panel){
        if(panel != null) {
            this.setOnClose(event -> {
                Animations.fadeOut(this, Duration.millis(300)).stop();
                panel.getChildren().remove(this);
            });
            panel.getChildren().add(this);
        }
    }
}
