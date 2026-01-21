package org.fxsql;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import javafx.beans.property.ReadOnlyDoubleProperty;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.exceptions.DriverNotFoundException;
import org.fxsql.utils.ConnectionStringParser;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public class PostgresSqlConnection extends AbstractDatabaseConnection {

    // The maximum number of rows to fetch for a query (e.g., for display/preview)
    private static final int ROW_LIMIT = 200;
    // Placeholder for the actual driver class name
    private static final String POSTGRES_DRIVER_CLASS = "org.postgresql.Driver";
    private static final Logger logger = Logger.getLogger(PostgresSqlConnection.class.getName());
    // In a real application, you'd extract these from the input 'connectionString'
    private String host;
    private int port = 5432; // Default PostgreSQL port
    private String database;
    private String user;
    private String password;

    // The full JDBC URL constructed during connect
    private String jdbcUrl;

    @Inject
    private DriverDownloader driverDownloader;

    @Override
    public void connect(String connectionString) throws SQLException, DriverNotFoundException {
        // ASSUMPTION: The connectionString is in a format that can be parsed,
        // e.g., "jdbc:postgresql://host:port/database?user=user&password=password"
        // or a custom format like "host=...;port=...;db=...;user=...;pass=..."

        // --- REAL IMPLEMENTATION would involve parsing 'connectionString' ---
        // For this completion, we'll use hardcoded values or a simple parsing logic:

        // Simple Example Parsing (replace with robust logic):
        // This is highly simplified and error-prone for real use.
//        String[] parts = connectionString.split(";");
//        for (String part : parts) {
//            if (part.startsWith("host=")) this.host = part.substring(5);
//            else if (part.startsWith("port=")) this.port = Integer.parseInt(part.substring(5));
//            else if (part.startsWith("db=")) this.database = part.substring(3);
//            else if (part.startsWith("user=")) this.user = part.substring(5);
//            else if (part.startsWith("pass=")) this.password = part.substring(5);
//        }

        // 1. Construct the JDBC URL
        ConnectionStringParser parser = new ConnectionStringParser();
        parser.parseConnectionString(connectionString);
        this.host = parser.getHost();
        this.port = parser.getPort();
        this.user = parser.getUser();
        this.password = parser.getPassword();
        if (Strings.isNullOrEmpty(this.user) || Strings.isNullOrEmpty(this.password)) {
            this.user = this.getUserName();
            this.password = this.getPassword();
        }
        this.database = parser.getDatabase();
        this.jdbcUrl = connectionString;


        // 2. Ensure the driver is loaded (it should have been downloaded first)
        if (!isDriverLoaded(POSTGRES_DRIVER_CLASS)) {
            throw new DriverNotFoundException(null);
        }

        // 3. Set properties for user and password
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
                // Ensure the connection is closed
                connection.close();
                this.connection = null; // Clear the connection reference
            } catch (SQLException e) {
                System.err.println("Error disconnecting from PostgreSQL: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isConnected() {
        if (connection == null) {
            return false;
        }
        try {
            // Check if the connection is closed or has become invalid
            // The argument '0' is the timeout in seconds for validation
            return !connection.isClosed() && connection.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public ReadOnlyDoubleProperty downloadDriverInTheBackground() {
        // Delegate to the dynamic loader. Assuming it returns a progress property.
        return dynamicJDBCDriverLoader.downloadDriverInTheBackground(POSTGRES_DRIVER_CLASS);
        // If DynamicJDBCDriverLoader is a simple placeholder and doesn't exist,
        // you might return a property that instantly shows 1.0 (downloaded):
        // return new SimpleDoubleProperty(1.0);
    }

    @Override
    public List<String> getTableNames() {
        List<String> tableNames = new ArrayList<>();

        // PostgreSQL query to get all table names in the public schema
        // You can use pg_catalog.pg_tables as well, but information_schema is SQL standard
        String sql = "SELECT table_name FROM information_schema.tables " + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'";

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) { // Use try-with-resources for safety

            while (rs.next()) {
                // 'table_name' is the standard column name in information_schema.tables
                tableNames.add(rs.getString("table_name"));
            }
            return tableNames;
        } catch (SQLException e) {
            System.err.println("Error getting PostgreSQL table names: " + e.getMessage());
            // Re-throw or handle as per application design
            return tableNames; // Return what was collected, or null if preferred
        }
    }

    @Override
    public ResultSet executeReadQuery(String sql) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Connection is not established or is closed.");
        }

        // 1. Create a Statement
        // Use createStatement(int resultSetType, int resultSetConcurrency)
        // to get a scrollable and read-only ResultSet, which is often useful in a UI client.
        Statement stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

        // 2. Set the fetch size and limit for preview/UI display
        // Fetch size hints the driver for performance.
        stmt.setFetchSize(ROW_LIMIT);

        // For PostgreSQL, adding "LIMIT n" to the SQL is the standard way to limit rows,
        // but we'll execute the raw SQL and rely on the UI client to stop reading
        // after ROW_LIMIT if necessary, or the user should add LIMIT to their query.
        // If the UI MUST limit, you'd prepend/append LIMIT to the SQL string here.

        // 3. Execute the query
        return stmt.executeQuery(sql);

        // NOTE: The caller of executeReadQuery is now responsible for closing
        // the Statement and the ResultSet.
    }

    @Override
    public int executeWriteQuery(String sql) throws SQLException {
        return executeWriteQuery(sql, connection, logger);
    }

    @Override
    public String connectionUrl() {
        // Return the constructed JDBC URL
        return this.jdbcUrl != null ? this.jdbcUrl : "";
    }
}