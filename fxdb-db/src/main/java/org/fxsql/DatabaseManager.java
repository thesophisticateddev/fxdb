package org.fxsql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import org.fxsql.encryption.EncryptionUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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

    // Add or update a connection
    public void addConnection(String name, String dbVendor, String host, String port, String user, String password, String url, String database, DatabaseConnection connection) {
        ConnectionMetaData connectionMetaData = new ConnectionMetaData();
        connectionMetaData.setDatabase(database);
        connectionMetaData.setDatabaseType(dbVendor);
        connectionMetaData.setUser(user);
        connectionMetaData.setHost(host);
        connectionMetaData.setPort(port);
        connectionMetaData.setUrl(url);
        connectionMetaData.setDatabaseConnection(connection);
        try {
            String encryptedPassword = EncryptionUtil.encrypt(password);
            connectionMetaData.setEncryptedPassword(encryptedPassword);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error occured while saving the connection", e);
        }

        if (connection.isConnected()) {
            // Close existing connection if updating
            if (connections.containsKey(name)) {
                ConnectionMetaData existing = connections.get(name);
                if (existing.getDatabaseConnection() != null && existing.getDatabaseConnection().isConnected()) {
                    existing.getDatabaseConnection().disconnect();
                }
                logger.info("Connection updated: " + name);
            } else {
                logger.info("Connection added: " + name);
            }
            connections.put(name, connectionMetaData);
            saveConnectionMetaData();
        } else {
            logger.severe("Connection not established with database!");
        }
    }

    public void addConnection(String name, String dbPath, String dbType, DatabaseConnection connection) {
        addConnection(name, dbPath, dbPath, null, connection);
    }

    public void addConnection(String name, String dbPath, String dbType, String port, DatabaseConnection connection) {
        ConnectionMetaData connectionMetaData = new ConnectionMetaData();
        connectionMetaData.setDatabaseFilePath(dbPath);
        connectionMetaData.setDatabaseConnection(connection);
        connectionMetaData.setDatabaseType(dbType);
        connectionMetaData.setPort(port);

        if (connection.isConnected()) {
            // Close existing connection if updating
            if (connections.containsKey(name)) {
                ConnectionMetaData existing = connections.get(name);
                if (existing.getDatabaseConnection() != null && existing.getDatabaseConnection().isConnected()) {
                    existing.getDatabaseConnection().disconnect();
                }
                logger.info("Connection updated: " + name);
            } else {
                logger.info("Connection added: " + name);
            }
            connections.put(name, connectionMetaData);
            saveConnectionMetaData();
        } else {
            logger.severe("Connection not established with database!");
        }
    }

    public DatabaseConnection connectByConnectionName(String name) throws Exception {
        ConnectionMetaData metaData = connections.get(name);
        if(metaData != null){
            // Establish Connection
            DatabaseConnection conn = DatabaseConnectionFactory.getConnection(metaData.getDatabaseType());

            String dbType = metaData.getDatabaseType();
            if (dbType != null && dbType.equalsIgnoreCase("sqlite")) {
                // For SQLite, use the file path (SqliteConnection.connect() handles the jdbc: prefix)
                String filePath = metaData.getDatabaseFilePath();
                if (filePath == null || filePath.isEmpty()) {
                    throw new IllegalStateException("SQLite database file path is not set");
                }
                conn.connect(filePath);
            } else if (metaData.getDatabaseFilePath() != null && !metaData.getDatabaseFilePath().isEmpty()) {
                // Other file-based databases
                conn.connect(metaData.getDatabaseFilePath());
            } else {
                // Network-based databases (MySQL, PostgreSQL)
                String url = metaData.getUrl();
                if (url == null || url.isEmpty()) {
                    throw new IllegalStateException("Database URL is not set for connection: " + name);
                }
                // Set credentials on the connection before connecting
                if (metaData.getUser() != null) {
                    conn.setUserName(metaData.getUser());
                }
                if (metaData.getEncryptedPassword() != null) {
                    try {
                        String decryptedPassword = EncryptionUtil.decrypt(metaData.getEncryptedPassword());
                        conn.setPassword(decryptedPassword);
                    } catch (Exception e) {
                        logger.warning("Failed to decrypt password for connection: " + name);
                    }
                }
                conn.connect(url);
            }

            metaData.setDatabaseConnection(conn);
            metaData.setConnected(conn.isConnected());
            return conn;
        }
        return null;
    }
    // Get a connection by name
    public DatabaseConnection getConnection(String name) {
        ConnectionMetaData metaData = connections.get(name);
        if (metaData != null) {
            return metaData.getDatabaseConnection();
        }
        return null;
    }

    public ConnectionMetaData getConnectionMetaData(String name) {
        return connections.get(name);
    }

    public void removeConnection(String name) {
        ConnectionMetaData metaData = connections.get(name);
        if (metaData != null) {
            // Close the connection if it's active
            DatabaseConnection conn = metaData.getDatabaseConnection();
            if (conn != null && conn.isConnected()) {
                conn.disconnect();
            }
        }
        connections.remove(name);
        // Persist the change
        saveConnectionMetaData();
        logger.info("Connection removed: " + name);
    }

    public void updateConnection(String name, ConnectionMetaData newMetaData) {
        connections.put(name, newMetaData);
        saveConnectionMetaData();
        logger.info("Connection updated: " + name);
    }

    private void saveConnectionMetaData() {
        try {
            //Create a JSON file if it does not exist
            // Set all connections inactive

            connections.values().forEach(v -> v.setConnected(false));
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
        } catch (Exception e) {
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
        Map<String, ConnectionMetaData> loadedConnections = null;
        ObjectMapper mapper = new ObjectMapper();

        try {
            // Explicitly defining the type to avoid LinkedHashMap issue
            loadedConnections = mapper.readValue(file, new TypeReference<>() {
            });

        } catch (Exception e) {
            logger.severe("Failed to load connections from META-DATA: " + e.getMessage());

            // Create a backup of the existing connections and save them
            String backupFilename = CONNECTION_DIRECTORY + "/" + "backup-connection-store-" + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + ".json";
            Path backupFilePath = Path.of(backupFilename);
            try {
                Files.copy(file.toPath(), backupFilePath);
            } catch (IOException ex) {
                e.printStackTrace();
            }
        }

        // Load the connections
        if (loadedConnections != null) {
            // Remove all connections here
            connections.clear();
            connections.putAll(loadedConnections);
        }

        logger.info("Connections loaded! Count: " + connections.size());
    }

}
