package org.fxsql;

import com.google.inject.Inject;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.driverload.JDBCDriverLoader;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public abstract class AbstractDatabaseConnection implements DatabaseConnection {

    protected Map<String, Object> metaData = new ConcurrentHashMap<>();

    // Handles downloading and loading the driver JAR
    protected final DynamicJDBCDriverLoader dynamicJDBCDriverLoader = new DynamicJDBCDriverLoader();
    protected Connection connection;

    @Inject
    private DriverDownloader driverDownloader;

    public AbstractDatabaseConnection() {
    }

    @Override
    public void setUserName(String username) {
        if (username != null) {
            metaData.put("username", username);
        }
    }

    @Override
    public void setPassword(String password) {
        if (password != null) {
            metaData.put("password", password);
        }
    }

    protected String getPassword() {
        Object pwd = metaData.get("password");
        return pwd != null ? pwd.toString() : "";
    }

    protected String getUserName() {
        Object user = metaData.get("username");
        return user != null ? user.toString() : "";
    }

    public boolean isWriteQuery(String sql) {
        if (sql == null) return false;
        String trimmed = sql.trim().toUpperCase();
        return trimmed.startsWith("INSERT") ||
                trimmed.startsWith("UPDATE") ||
                trimmed.startsWith("DELETE") ||
                trimmed.startsWith("CREATE") ||
                trimmed.startsWith("DROP") ||
                trimmed.startsWith("ALTER") ||
                trimmed.startsWith("TRUNCATE") ||
                trimmed.startsWith("MERGE");
    }

    /**
     * Executes a write query (INSERT, UPDATE, DELETE, etc.) and returns the number of affected rows.
     *
     * @param sql The SQL statement to execute
     * @param conn The database connection to use
     * @param logger The logger for logging the operation
     * @return The number of rows affected
     * @throws SQLException if a database access error occurs
     */
    public int executeWriteQuery(String sql, Connection conn, Logger logger) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection is not established or is closed.");
        }

        try (Statement stmt = conn.createStatement()) {
            int rowsAffected = stmt.executeUpdate(sql);
            logger.info("Write query executed. Rows affected: " + rowsAffected);
            return rowsAffected;
        }
    }

    public boolean isDriverLoaded(String className) {
        var drivers = DriverManager.getDrivers().asIterator();
        while (drivers.hasNext()) {
            Driver d = drivers.next();
            // Handle both shim and regular drivers
            if (d instanceof JDBCDriverLoader.JDBCDriverShim shim) {
                if (shim.driver().getClass().getName().contains(className)) {
                    return true;
                }
            } else if (d instanceof DriverShim driverShim) {
                if (driverShim.getDriver().getClass().getName().contains(className)) {
                    return true;
                }
            } else if (d.getClass().getName().contains(className)) {
                return true;
            }
        }
        return false;
    }
}