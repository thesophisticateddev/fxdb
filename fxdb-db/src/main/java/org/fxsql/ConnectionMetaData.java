package org.fxsql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConnectionMetaData {

    private String databaseType;
    private String user;
    private String encryptedPassword;
    private String database;
    private String databaseFilePath;
    private String url;
    @JsonIgnore
    private DatabaseConnection DatabaseConnection;
    private String host;
    private String port;
    private boolean isFileBased;
    private boolean isConnected;

    @JsonIgnore
    public DatabaseConnection getDatabaseConnection() {
        return DatabaseConnection;
    }

    public ConnectionMetaData(){}
    @JsonIgnore
    public void setDatabaseConnection(DatabaseConnection DatabaseConnection) {
        this.DatabaseConnection = DatabaseConnection;
    }

    public static ConnectionMetaData mapFromObject(Object obj){
        ConnectionMetaData metaData = (ConnectionMetaData) obj;

        return metaData;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getDatabaseFilePath() {
        return databaseFilePath;
    }

    public void setDatabaseFilePath(String databaseFilePath) {
        this.databaseFilePath = databaseFilePath;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }
}
