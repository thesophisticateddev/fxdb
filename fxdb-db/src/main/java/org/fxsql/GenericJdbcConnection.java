package org.fxsql;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * A generic JDBC connection that can work with any JDBC-compliant database.
 * This is used for databases that don't have a specialized connection class.
 */
public class GenericJdbcConnection extends AbstractDatabaseConnection {

    private static final int ROW_LIMIT = 200;
    private static final Logger logger = Logger.getLogger(GenericJdbcConnection.class.getName());

    private String jdbcUrl;
    private String databaseType;

    public GenericJdbcConnection() {
        this("generic");
    }

    public GenericJdbcConnection(String databaseType) {
        this.databaseType = databaseType;
    }

    @Override
    public void connect(String connectionString) throws SQLException {
        this.jdbcUrl = connectionString;

        Properties props = new Properties();
        String user = getUserName();
        String pass = getPassword();

        if (user != null && !user.isEmpty()) {
            props.setProperty("user", user);
        }
        if (pass != null && !pass.isEmpty()) {
            props.setProperty("password", pass);
        }

        try {
            this.connection = DriverManager.getConnection(jdbcUrl, props);
            logger.info("Connected to " + databaseType + " database: " + jdbcUrl);
        } catch (SQLException e) {
            logger.severe("Failed to connect to " + databaseType + ": " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                this.connection = null;
                logger.info("Disconnected from " + databaseType + " database");
            } catch (SQLException e) {
                logger.warning("Error disconnecting from " + databaseType + ": " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isConnected() {
        if (connection == null) {
            return false;
        }
        try {
            return !connection.isClosed() && connection.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public ReadOnlyDoubleProperty downloadDriverInTheBackground() {
        // Generic connection doesn't handle driver downloads
        return new SimpleDoubleProperty(1.0);
    }

    @Override
    public List<String> getTableNames() {
        List<String> tableNames = new ArrayList<>();

        if (connection == null) {
            return tableNames;
        }

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            // Try to get tables from the default schema
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // Filter out system tables
                    if (!isSystemTable(tableName)) {
                        tableNames.add(tableName);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning("Error getting table names: " + e.getMessage());
        }

        return tableNames;
    }

    /**
     * Filters out common system tables.
     */
    private boolean isSystemTable(String tableName) {
        if (tableName == null) return true;
        String lower = tableName.toLowerCase();
        return lower.startsWith("sys") ||
                lower.startsWith("pg_") ||
                lower.startsWith("information_schema") ||
                lower.startsWith("mysql.") ||
                lower.startsWith("sqlite_");
    }

    @Override
    public ResultSet executeReadQuery(String sql) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Connection is not established or is closed.");
        }

        Statement stmt = connection.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY
        );
        stmt.setFetchSize(ROW_LIMIT);
        stmt.setMaxRows(ROW_LIMIT);

        return stmt.executeQuery(sql);
    }

    @Override
    public int executeWriteQuery(String sql) throws SQLException {
        return executeWriteQuery(sql, connection, logger);
    }

    @Override
    public String connectionUrl() {
        return jdbcUrl != null ? jdbcUrl : "";
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }
}
