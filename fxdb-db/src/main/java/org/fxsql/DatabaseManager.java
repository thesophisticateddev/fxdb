package org.fxsql;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class DatabaseManager  {
    private final Map<String, DatabaseConnection> connections = new HashMap<>();

    public DatabaseManager(){}


    // Close all connections
    public void closeAll() {
        for (DatabaseConnection conn : connections.values()) {
            if (conn != null && !conn.isConnected()) {
                conn.disconnect();
            }
        }
        connections.clear();
    }

    // Add a new connection
    public void addConnection(String name, DatabaseConnection connection) {
        if (!connections.containsKey(name)) {

            if (connection.isConnected()) {
                connections.put(name, connection);
            }

        }
    }

    // Get a connection by name
    public DatabaseConnection getConnection(String name) {
        return connections.get(name);
    }
}
