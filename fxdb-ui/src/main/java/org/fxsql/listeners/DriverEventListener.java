package org.fxsql.listeners;

import javafx.application.Platform;
import javafx.event.EventHandler;
import org.fxsql.DriverDownloadEvent;
import org.fxsql.DriverLoadedEvent;
import org.fxsql.EventBus;
import org.fxsql.components.notifications.DriverDownloadNotificationComplete;

public class DriverEventListener extends BaseListener {

    public DriverEventListener() {
      setDriverDownloadedEventListener();
      setDriverLoadedEventListener();
    }


    private void setDriverDownloadedEventListener(){
        EventHandler<DriverDownloadEvent> onDatabaseEvent = event -> Platform.runLater(() -> {
            // Display notification using AtlantaFX
            DriverDownloadNotificationComplete notificationComplete =
                    new DriverDownloadNotificationComplete(event.getMessage());
            notificationComplete.show(notificationPanel);

        });
        EventBus.addEventHandler(DriverDownloadEvent.DRIVER_DOWNLOAD_EVENT, onDatabaseEvent);
    }

    private void setDriverLoadedEventListener(){
        EventHandler<DriverLoadedEvent> onDatabaseEvent = event -> Platform.runLater(() -> {
            // Display notification using AtlantaFX
            DriverDownloadNotificationComplete notificationComplete =
                    new DriverDownloadNotificationComplete(event.getMessage());
            notificationComplete.show(notificationPanel);

        });
        EventBus.addEventHandler(DriverLoadedEvent.DRIVER_LOADED_EVENT, onDatabaseEvent);
    }
}
