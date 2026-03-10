package org.fxsql.events;

import javafx.event.Event;
import javafx.event.EventType;

public class FxdbDockEvent<T> extends Event {

    private final T payload;

    public FxdbDockEvent(EventType<? extends Event> type, T payload) {
        super(type);
        this.payload = payload;
    }

    public T getPayload() {
        return payload;
    }
}
