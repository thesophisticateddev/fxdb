package org.fxsql;


import javafx.beans.property.ReadOnlyDoubleProperty;
import org.fxsql.driverload.JDBCDriverLoader;

import java.sql.*;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface DatabaseConnection {

    void connect(String connection) throws Exception;
    void disconnect();

    boolean isConnected();

//    void setDownloadProgressBar(ProgressBar pb);

    ReadOnlyDoubleProperty downloadDriverInTheBackground();

    List<String> getTableNames();

    /**
     * Returns a list of view names in the database.
     * @return List of view names, or empty list if not supported
     */
    default List<String> getViewNames() {
        return Collections.emptyList();
    }

    /**
     * Returns a list of trigger names in the database.
     * @return List of trigger names, or empty list if not supported
     */
    default List<String> getTriggerNames() {
        return Collections.emptyList();
    }

    /**
     * Returns a list of function/procedure names in the database.
     * @return List of function names, or empty list if not supported
     */
    default List<String> getFunctionNames() {
        return Collections.emptyList();
    }

    /**
     * Returns a list of index names in the database.
     * @return List of index names, or empty list if not supported
     */
    default List<String> getIndexNames() {
        return Collections.emptyList();
    }

    /**
     * Returns all database objects grouped by type.
     * @return DatabaseObjects containing all database objects
     */
    default DatabaseObjects getAllDatabaseObjects() {
        return new DatabaseObjects(
            getTableNames(),
            getViewNames(),
            getTriggerNames(),
            getFunctionNames(),
            getIndexNames()
        );
    }

    ResultSet executeReadQuery(String sql) throws SQLException;
    int executeWriteQuery(String sql)  throws SQLException;

    default int executeWriteQuery(String sql, Connection connection, Logger logger) throws SQLException{
        // Validation
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be null or empty");
        }

        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection is not available");
        }

        Statement stmt = null;
        try {
            stmt = connection.createStatement();

            // Note: setMaxRows() only affects SELECT queries, not write queries
            // It's not harmful to set it, but it won't limit INSERT/UPDATE/DELETE operations

            // Determine the appropriate execution method
            String trimmedSql = sql.trim().toUpperCase();

            // Use executeUpdate for DML (INSERT, UPDATE, DELETE) and DDL (CREATE, DROP, ALTER)
            // These return the number of affected rows or 0 for DDL statements
            int affectedRows = stmt.executeUpdate(sql);

            logger.info("Write query executed successfully. Affected rows: " + affectedRows);
            return affectedRows;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to execute write query: " + sql, e);
            // Re-throw SQLException so the caller can handle it
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error executing write query: " + sql, e);
            // Wrap unexpected exceptions in SQLException
            throw new SQLException("Unexpected error executing query", e);
        } finally {
            // Always close the statement to prevent resource leaks
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Failed to close statement", e);
                    // Don't throw here - we want to preserve the original exception if any
                }
            }
        }
    };


    String connectionUrl();

    void setUserName(String username);
    void setPassword(String password);

}
