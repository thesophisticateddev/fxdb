package org.fxsql;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.concurrent.Task;
import javafx.scene.control.ProgressBar;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MySqlConnection implements DatabaseConnection {

    // The maximum number of rows to fetch for a query (e.g., for display/preview)
    private static final int ROW_LIMIT = 200;

    // The official MySQL JDBC Driver class name (Connector/J)
    private static final String MYSQL_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    // Properties to store connection details
    private String host;
    private int port = 3306; // Default MySQL port
    private String database;
    private String user;
    private String password;

    // The full JDBC URL constructed during connect
    private String jdbcUrl;

    private final DynamicJDBCDriverLoader dynamicJDBCDriverLoader = new DynamicJDBCDriverLoader();
    private Connection connection; // The active JDBC connection

    // Note: The ProgressBar field and commented method are omitted as they are
    // likely handled by the DynamicJDBCDriverLoader or the calling class via ReadOnlyDoubleProperty.

    @Override
    public void connect(String connectionString) throws SQLException {
        // ASSUMPTION: Parsing 'connectionString' into host, port, db, user, pass
        // Example format for connectionString: "host=127.0.0.1;port=3306;db=testdb;user=root;pass=password"

        // --- REAL IMPLEMENTATION would involve robust parsing ---
        String[] parts = connectionString.split(";");
        for (String part : parts) {
            if (part.startsWith("host=")) this.host = part.substring(5);
            else if (part.startsWith("port=")) this.port = Integer.parseInt(part.substring(5));
            else if (part.startsWith("db=")) this.database = part.substring(3);
            else if (part.startsWith("user=")) this.user = part.substring(5);
            else if (part.startsWith("pass=")) this.password = part.substring(5);
        }

        // 1. Construct the JDBC URL
        // Added 'serverTimezone=UTC' and 'useSSL=false' for common modern MySQL compatibility
        // These are often required when using Connector/J 8.0+
        this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?serverTimezone=UTC&useSSL=false";

        // 2. Ensure the driver is loaded
        try {
            Class.forName(MYSQL_DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found. Ensure the driver is loaded.", e);
        }

        // 3. Set properties for connection (user/password can be passed in URL or Properties)
        Properties props = new Properties();
        props.setProperty("user", this.user);
        props.setProperty("password", this.password);

        // 4. Establish the connection
        this.connection = DriverManager.getConnection(this.jdbcUrl, props);
    }

    @Override
    public void disconnect() {
        if (connection != null) {
            try {
                // Close the active connection
                connection.close();
                this.connection = null;
            } catch (SQLException e) {
                System.err.println("Error disconnecting from MySQL: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isConnected() {
        if (connection == null) {
            return false;
        }
        try {
            // Check if the connection is closed and validate it
            // '1' is the timeout in seconds for validation
            return !connection.isClosed() && connection.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public ReadOnlyDoubleProperty downloadDriverInTheBackground() {
        // Delegate to the dynamic loader to download the MySQL driver JAR
        // Assuming 'mysql' is a known alias for the MySQL driver in the loader
        return dynamicJDBCDriverLoader.downloadDriverInTheBackground("mysql");
    }

    @Override
    public List<String> getTableNames() {
        List<String> tableNames = new ArrayList<>();

        // MySQL query to get all table names in the currently connected database
        // Uses the standard SQL query against information_schema
        String sql = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tableNames.add(rs.getString("table_name"));
            }
            return tableNames;
        }
        catch (SQLException e) {
            System.err.println("Error getting MySQL table names: " + e.getMessage());
            return tableNames;
        }
    }

    @Override
    public ResultSet executeReadQuery(String sql) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Connection is not established or is closed.");
        }

        // 1. Create a Statement
        Statement stmt = connection.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE, // Allows scrolling for UI display
                ResultSet.CONCUR_READ_ONLY
        );

        // 2. Set the fetch size and limit. This is a hint to the driver.
        stmt.setFetchSize(ROW_LIMIT);

        // 3. Execute the query
        return stmt.executeQuery(sql);

        // NOTE: The caller is responsible for closing the Statement and the ResultSet.
    }

    @Override
    public String connectionUrl() {
        // Return the constructed JDBC URL without credentials
        return this.jdbcUrl != null ? this.jdbcUrl : "";
    }
}