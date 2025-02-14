package org.fxsql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Singleton
public class DatabaseManager {
    private final Map<String, DatabaseConnection> connections = new HashMap<>();
    private final Logger logger = Logger.getLogger(DatabaseManager.class.getName());
    private final String CONNECTION_STORE = "connection_store.json";
    private final String CONNECTION_DIRECTORY = "META-DATA";

    public DatabaseManager() {
    }


    // Close all connections
    public void closeAll() {
        saveConnectionMetaData();
        for (DatabaseConnection conn : connections.values()) {
            if (conn != null && !conn.isConnected()) {
                conn.disconnect();
            }
        }
        connections.clear();
        logger.info("All database connections closed");
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

    public void removeConnection(String name) {
        connections.remove(name);
    }

    private void saveConnectionMetaData() {
        try {
            //Create a JSON file if it does not exist

            //Define the directory of the file
            File directory = new File(CONNECTION_DIRECTORY);
            if (!directory.exists()) {
                directory.mkdir();
            }

            File file = new File(CONNECTION_DIRECTORY, CONNECTION_STORE);

            Map<String, ConnectionMetaData> mappedData = new HashMap<>(connections.size());

            connections.keySet().forEach(key -> {
                DatabaseConnection connection = connections.get(key);
                String connectionUrl = connection.connectionUrl();
                ConnectionMetaData metaData = new ConnectionMetaData();
                metaData.setDatabaseFilePath(connectionUrl);
                mappedData.put(key, metaData);
            });
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(file, mappedData);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


}
