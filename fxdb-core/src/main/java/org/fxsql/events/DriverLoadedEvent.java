package org.fxsql.events;

import javafx.event.Event;
import javafx.event.EventType;

public class DriverLoadedEvent extends Event implements IEvent {

    public static final EventType<DriverLoadedEvent> DRIVER_LOADED_EVENT =
            new EventType<>(Event.ANY, "DRIVER_LOADED");
    private final String message;
    public DriverLoadedEvent(String message){
        super(DRIVER_LOADED_EVENT);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
