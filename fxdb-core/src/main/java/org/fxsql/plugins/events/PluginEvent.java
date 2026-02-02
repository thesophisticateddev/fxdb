package org.fxsql.plugins.events;

import javafx.event.Event;
import javafx.event.EventType;
import org.fxsql.events.IEvent;

/**
 * Event fired when plugin state changes.
 */
public class PluginEvent extends Event implements IEvent {

    public static final EventType<PluginEvent> PLUGIN_EVENT =
            new EventType<>(Event.ANY, "PLUGIN_EVENT");

    public static final EventType<PluginEvent> PLUGIN_INSTALLED =
            new EventType<>(PLUGIN_EVENT, "PLUGIN_INSTALLED");

    public static final EventType<PluginEvent> PLUGIN_UNINSTALLED =
            new EventType<>(PLUGIN_EVENT, "PLUGIN_UNINSTALLED");

    public static final EventType<PluginEvent> PLUGIN_STARTED =
            new EventType<>(PLUGIN_EVENT, "PLUGIN_STARTED");

    public static final EventType<PluginEvent> PLUGIN_STOPPED =
            new EventType<>(PLUGIN_EVENT, "PLUGIN_STOPPED");

    public static final EventType<PluginEvent> PLUGIN_ERROR =
            new EventType<>(PLUGIN_EVENT, "PLUGIN_ERROR");

    public static final EventType<PluginEvent> PLUGIN_LOADED =
            new EventType<>(PLUGIN_EVENT, "PLUGIN_LOADED");

    private final String message;
    private final String pluginId;

    public PluginEvent(EventType<PluginEvent> eventType, String message, String pluginId) {
        super(eventType);
        this.message = message;
        this.pluginId = pluginId;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getPluginId() {
        return pluginId;
    }
}