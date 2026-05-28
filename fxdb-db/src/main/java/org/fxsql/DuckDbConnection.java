package org.fxsql;

import javafx.beans.property.ReadOnlyDoubleProperty;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class DuckDbConnection extends AbstractDatabaseConnection {

    private static final int ROW_LIMIT = 200;
    private static final String DUCKDB_DRIVER_CLASS = "org.duckdb.DuckDBDriver";
    private static final Logger logger = Logger.getLogger(DuckDbConnection.class.getName());

    private String jdbcUrl;

    @Override
    public void connect(String connectionString) throws Exception {
        // Step 1 — normalize URL
        if (connectionString == null || connectionString.isBlank()
                || connectionString.equals(":memory:")) {
            this.jdbcUrl = "jdbc:duckdb:";
        } else if (!connectionString.startsWith("jdbc:")) {
            this.jdbcUrl = "jdbc:duckdb:" + connectionString;
        } else {
            this.jdbcUrl = connectionString;
        }

        // Step 2 — validate scheme (OWASP A03): reject anything that is not a DuckDB URL.
        if (!this.jdbcUrl.startsWith("jdbc:duckdb:")) {
            throw new IllegalArgumentException(
                    "Unsupported JDBC scheme for DuckDbConnection — expected jdbc:duckdb:");
        }

        // Ensure driver is loaded
        if (!DynamicJDBCDriverLoader.isDriverAlreadyLoaded(DUCKDB_DRIVER_CLASS)) {
            boolean loaded = DynamicJDBCDriverLoader.loadDuckDBJDBCDriver();
            if (!loaded) {
                throw new RuntimeException("DuckDB driver is not available. Please download the driver first.");
            }
        }

        final String url = this.jdbcUrl;
        try {
            if (DynamicJDBCDriverLoader.isDriverClassLoaderRegistered(DUCKDB_DRIVER_CLASS)) {
                this.connection = DynamicJDBCDriverLoader.withDriverTCCL(
                        DUCKDB_DRIVER_CLASS,
                        () -> DriverManager.getConnection(url)
                );
            } else {
                this.connection = DriverManager.getConnection(url);
            }
            // Log type only — never log the full URL (OWASP A09: no paths or tokens in logs)
            logger.info("Connected to DuckDB (" + (url.equals("jdbc:duckdb:") ? "in-memory" : "file") + ")");
        } catch (SQLException e) {
            logger.severe("Failed to connect to DuckDB: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warning("Error closing DuckDB connection: " + e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public ReadOnlyDoubleProperty downloadDriverInTheBackground() {
        return dynamicJDBCDriverLoader.downloadDriverInTheBackground(DUCKDB_DRIVER_CLASS);
    }

    @Override
    public List<String> getTableNames() {
        List<String> tableNames = new ArrayList<>();
        if (connection == null) return tableNames;
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = 'main' AND table_type = 'BASE TABLE' ORDER BY table_name";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tableNames.add(rs.getString("table_name"));
            }
        } catch (SQLException e) {
            logger.warning("Error getting DuckDB table names: " + e.getMessage());
        }
        return tableNames;
    }

    @Override
    public List<String> getViewNames() {
        List<String> viewNames = new ArrayList<>();
        if (connection == null) return viewNames;
        String sql = "SELECT table_name FROM information_schema.views WHERE table_schema = 'main' ORDER BY table_name";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                viewNames.add(rs.getString("table_name"));
            }
        } catch (SQLException e) {
            logger.warning("Error getting DuckDB view names: " + e.getMessage());
        }
        return viewNames;
    }

    @Override
    public List<String> getFunctionNames() {
        List<String> functionNames = new ArrayList<>();
        if (connection == null) return functionNames;
        String sql = "SELECT DISTINCT function_name FROM duckdb_functions() WHERE schema_name = 'main' AND (function_type = 'scalar' OR function_type = 'aggregate' OR function_type = 'macro') ORDER BY function_name";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                functionNames.add(rs.getString("function_name"));
            }
        } catch (SQLException e) {
            logger.fine("duckdb_functions() not available or error: " + e.getMessage());
        }
        return functionNames;
    }

    @Override
    public List<String> getIndexNames() {
        List<String> indexNames = new ArrayList<>();
        if (connection == null) return indexNames;
        String sql = "SELECT index_name FROM duckdb_indexes() WHERE schema_name = 'main' ORDER BY index_name";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                indexNames.add(rs.getString("index_name"));
            }
        } catch (SQLException e) {
            logger.fine("duckdb_indexes() not available or error: " + e.getMessage());
        }
        return indexNames;
    }

    @Override
    public List<String> getTriggerNames() {
        return Collections.emptyList();
    }

    @Override
    public String getViewDefinition(String viewName) {
        if (connection == null) return "";
        String sql = "SELECT sql FROM duckdb_views() WHERE schema_name = 'main' AND view_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, viewName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String def = rs.getString("sql");
                    return def != null ? def : "";
                }
            }
        } catch (SQLException e) {
            logger.warning("Error getting DuckDB view definition: " + e.getMessage());
        }
        return "";
    }

    @Override
    public ResultSet executeReadQuery(String sql) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Connection is not established or is closed.");
        }
        Statement stmt = connection.createStatement();
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
}
