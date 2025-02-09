package org.fxsql.services;

import atlantafx.base.controls.Notification;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.controlsfx.control.NotificationPane;

public class NotificationManager {
    private static Stage mainStage;

    public static void setMainStage(Stage stage){
        mainStage = stage;
    }
    private static void showNotification( Notification notification) {
        if (mainStage == null) {
            System.err.println("Main stage is not set for notifications!");
            return;
        }

        Platform.runLater(() -> {
            NotificationPane pane = new NotificationPane();

            // Show notification on main stage
            pane.show();

        });
    }
}
