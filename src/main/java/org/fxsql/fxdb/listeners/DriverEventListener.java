package org.fxsql.fxdb.listeners;

import javafx.application.Platform;
import javafx.event.EventHandler;
import org.fxsql.fxdb.components.notifications.DriverDownloadNotificationComplete;
import org.fxsql.fxdb.components.notifications.DriverLoadedNotification;
import org.fxsql.fxdb.services.core.events.DriverDownloadEvent;
import org.fxsql.fxdb.services.core.events.DriverLoadedEvent;
import org.fxsql.fxdb.services.core.events.EventBus;


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
            DriverLoadedNotification notificationComplete =
                    new DriverLoadedNotification(event.getMessage());
            notificationComplete.show(notificationPanel);

        });
        EventBus.addEventHandler(DriverLoadedEvent.DRIVER_LOADED_EVENT, onDatabaseEvent);
    }
}
