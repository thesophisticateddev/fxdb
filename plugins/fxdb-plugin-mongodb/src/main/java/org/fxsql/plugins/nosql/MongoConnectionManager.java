package org.fxsql.plugins.nosql;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class MongoConnectionManager {

    private static final Logger logger = Logger.getLogger(MongoConnectionManager.class.getName());

    private MongoClient client;
    private String currentHost;
    private int currentPort;

    public void connect(String host, int port, String database,
                        String username, String password, String authDatabase) {
        disconnect();

        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applyToClusterSettings(builder ->
                        builder.hosts(List.of(new ServerAddress(host, port)))
                                .serverSelectionTimeout(5, TimeUnit.SECONDS));

        if (username != null && !username.isBlank()
                && password != null && !password.isBlank()) {
            String authDb = (authDatabase != null && !authDatabase.isBlank())
                    ? authDatabase : "admin";
            MongoCredential credential = MongoCredential.createCredential(
                    username, authDb, password.toCharArray());
            settingsBuilder.credential(credential);
        }

        client = MongoClients.create(settingsBuilder.build());
        currentHost = host;
        currentPort = port;

        // Ping to verify the connection works
        String db = (database != null && !database.isBlank()) ? database : "admin";
        client.getDatabase(db).runCommand(new Document("ping", 1));

        logger.info("Connected to MongoDB at " + host + ":" + port);
    }

    public void disconnect() {
        if (client != null) {
            client.close();
            client = null;
            logger.info("Disconnected from MongoDB");
        }
    }

    public boolean isConnected() {
        return client != null;
    }

    public List<String> listDatabaseNames() {
        List<String> names = new ArrayList<>();
        if (client != null) {
            client.listDatabaseNames().forEach(names::add);
        }
        return names;
    }

    public List<String> listCollectionNames(String databaseName) {
        List<String> names = new ArrayList<>();
        if (client != null) {
            client.getDatabase(databaseName)
                    .listCollectionNames()
                    .forEach(names::add);
        }
        return names;
    }

    public List<Document> findDocuments(String databaseName, String collectionName, int limit) {
        List<Document> docs = new ArrayList<>();
        if (client != null) {
            client.getDatabase(databaseName)
                    .getCollection(collectionName)
                    .find()
                    .limit(limit)
                    .forEach(docs::add);
        }
        return docs;
    }

    public long countDocuments(String databaseName, String collectionName) {
        if (client != null) {
            return client.getDatabase(databaseName)
                    .getCollection(collectionName)
                    .countDocuments();
        }
        return 0;
    }

    public String getCurrentHost() {
        return currentHost;
    }

    public int getCurrentPort() {
        return currentPort;
    }
}
