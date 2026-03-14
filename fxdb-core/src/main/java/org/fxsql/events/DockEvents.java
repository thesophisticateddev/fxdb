package org.fxsql.events;

import javafx.event.Event;
import javafx.event.EventType;

public final class DockEvents {

    public static final EventType<Event> DOCK_EVENT =
        new EventType<>(Event.ANY, "DOCK_EVENT");

    public static final EventType<Event> CONNECTION_SELECTED =
        new EventType<>(DOCK_EVENT, "CONNECTION_SELECTED");

    public static final EventType<Event> TABLE_SELECTED =
        new EventType<>(DOCK_EVENT, "TABLE_SELECTED");

    public static final EventType<Event> QUERY_EXECUTED =
        new EventType<>(DOCK_EVENT, "QUERY_EXECUTED");

    public static final EventType<Event> LOG_MESSAGE =
        new EventType<>(DOCK_EVENT, "LOG_MESSAGE");

    // Fired by FileExplorerPane when the user double-clicks a .sql file
    // Payload: java.nio.file.Path
    public static final EventType<Event> SQL_FILE_OPENED =
        new EventType<>(DOCK_EVENT, "SQL_FILE_OPENED");

    // Fired by WorkspaceExplorerPane when the user clicks "Load Workspace"
    // Payload: org.fxsql.workspace.Workspace
    public static final EventType<Event> WORKSPACE_LOADED =
        new EventType<>(DOCK_EVENT, "WORKSPACE_LOADED");

    private DockEvents() {}
}
