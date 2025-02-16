package org.fxsql.events;

import javafx.event.Event;
import javafx.event.EventType;

public class NewConnectionAddedEvent extends Event implements IEvent {

    public static final EventType<NewConnectionAddedEvent> NEW_CONNECTION_ADDED =
            new EventType<>(Event.ANY, "NEW_CONNECTION_ADDED");

    private String message;
    public NewConnectionAddedEvent(String msg){
        super(NEW_CONNECTION_ADDED);
        message = msg;
    }
    @Override
    public String getMessage() {
        return message;
    }
}
