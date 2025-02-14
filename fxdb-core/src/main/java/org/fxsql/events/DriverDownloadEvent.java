package org.fxsql.events;

import javafx.event.Event;
import javafx.event.EventType;

public class DriverDownloadEvent extends Event implements IEvent {
    public static final EventType<DriverDownloadEvent> DRIVER_DOWNLOAD_EVENT =
            new EventType<>(Event.ANY, "DRIVER_DOWNLOAD");
    private final String message;

    public DriverDownloadEvent(String message) {
        super(DRIVER_DOWNLOAD_EVENT);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
