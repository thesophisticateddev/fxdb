package org.fxsql;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventBus {
    private static final Map<EventType<?>, List<EventHandler<? super Event>>> listeners = new HashMap<>();

    public static <T extends Event> void addEventHandler(EventType<T> type, EventHandler<? super T> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add((EventHandler<? super Event>) listener);
    }

    public static void fireEvent(Event event) {
        List<EventHandler<? super Event>> eventHandlers = listeners.get(event.getEventType());
        if (eventHandlers != null) {
            for (EventHandler<? super Event> handler : eventHandlers) {
                handler.handle(event);
            }
        }
    }
}