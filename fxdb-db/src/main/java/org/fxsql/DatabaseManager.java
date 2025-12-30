package org.fxsql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import org.fxsql.encryption.EncryptionUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class DatabaseManager {
    private final static Map<String, ConnectionMetaData> connections = new HashMap<>();
    private final Logger logger = Logger.getLogger(DatabaseManager.class.getName());
    private final String CONNECTION_STORE = "connection_store.json";
    private final String CONNECTION_DIRECTORY = "META-DATA";

    public DatabaseManager() {
    }


    // Close all connections
    public void closeAll() {
        saveConnectionMetaData();
        for (ConnectionMetaData conn : connections.values()) {
            DatabaseConnection connection = conn.getDatabaseConnection();
            if (connection != null && connection.isConnected()) {
                connection.disconnect();
            }
        }
        connections.clear();
        logger.info("All database connections closed");
    }

    // Add a new connection
    public void addConnection(String name, String dbVendor, String host, String port, String user, String password,
                              DatabaseConnection connection) {
        ConnectionMetaData connectionMetaData = new ConnectionMetaData();
        connectionMetaData.setDatabase(dbVendor);
        connectionMetaData.setDatabaseType(dbVendor);
        connectionMetaData.setUser(user);
        connectionMetaData.setHost(host);
        connectionMetaData.setPort(port);
        connectionMetaData.setDatabaseConnection(connection);
        try {
            String encryptedPassword = EncryptionUtil.encrypt(password);
            connectionMetaData.setEncryptedPassword(encryptedPassword);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Error occured while saving the connection",e);
        }

        if (!connections.containsKey(name)) {
            if (connection.isConnected()) {
                connections.put(name, connectionMetaData);
            }
            else {
                logger.severe("Connection not established with database!");
            }

        }
    }

    public void addConnection(String name, String dbPath, String dbType,DatabaseConnection connection) {
        ConnectionMetaData connectionMetaData = new ConnectionMetaData();
        connectionMetaData.setDatabaseFilePath(dbPath);
        connectionMetaData.setDatabaseConnection(connection);
        connectionMetaData.setDatabaseType(dbType);
        if (!connections.containsKey(name)) {
            if (connection.isConnected()) {
                connections.put(name, connectionMetaData);
                logger.info("Connection Added! Name: " + name);
            }
            else {
                logger.severe("Connection not established with database!");
            }

        }
    }

    // Get a connection by name
    public DatabaseConnection getConnection(String name) {
        ConnectionMetaData metaData = connections.get(name);
        if (metaData != null) {
            return metaData.getDatabaseConnection();
        }
        return null;
    }

    public ConnectionMetaData getConnectionMetaData(String name){
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

            ObjectMapper mapper = new ObjectMapper();
            // Write JSON string to file manually
            mapper.writeValue(file, connections);
            logger.info("Connection meta data saved!");
        }
        catch (Exception e) {
            logger.severe("Connections could not saved due to error: " + e.getMessage());
        }
    }

    public Set<String> getConnectionList() {
        return connections.keySet();
    }

    public void loadStoredConnections() {
        File file = new File(CONNECTION_DIRECTORY, CONNECTION_STORE);

        // Check if the file exists
        if (!file.exists()) {
            logger.warning("No saved connection metadata found.");
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            // Explicitly defining the type to avoid LinkedHashMap issue
            Map<String, ConnectionMetaData> loadedConnections =
                    mapper.readValue(file, new TypeReference<>() {
                    });
            // Remove all connections here
            connections.clear();
            // Load the connections
            connections.putAll(loadedConnections);
            logger.info("Connections loaded! Count: " + connections.size());
        }
        catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

}
