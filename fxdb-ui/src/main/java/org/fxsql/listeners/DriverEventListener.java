package org.fxsql.listeners;

import javafx.application.Platform;
import javafx.event.EventHandler;
import org.fxsql.events.DriverDownloadEvent;
import org.fxsql.events.DriverLoadedEvent;
import org.fxsql.events.EventBus;

/**
 * Listens for driver-related events and shows notifications.
 */
public class DriverEventListener extends BaseListener {

    public DriverEventListener() {
        setDriverDownloadedEventListener();
        setDriverLoadedEventListener();
    }

    private void setDriverDownloadedEventListener() {
        EventHandler<DriverDownloadEvent> onDriverDownload = event -> Platform.runLater(() -> {
            if (notificationContainer != null) {
                notificationContainer.showSuccess("Driver downloaded: " + event.getMessage());
            }
        });
        EventBus.addEventHandler(DriverDownloadEvent.DRIVER_DOWNLOAD_EVENT, onDriverDownload);
    }

    private void setDriverLoadedEventListener() {
        EventHandler<DriverLoadedEvent> onDriverLoaded = event -> Platform.runLater(() -> {
            if (notificationContainer != null) {
                notificationContainer.showInfo("Driver loaded: " + event.getMessage());
            }
        });
        EventBus.addEventHandler(DriverLoadedEvent.DRIVER_LOADED_EVENT, onDriverLoaded);
    }
}