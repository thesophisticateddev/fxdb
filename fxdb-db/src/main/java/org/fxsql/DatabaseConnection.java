package org.fxsql;


import javafx.beans.property.ReadOnlyDoubleProperty;
import org.fxsql.driverload.JDBCDriverLoader;
import org.fxsql.model.TableMetaData;

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

    /**
     * Returns the underlying JDBC connection.
     * @return The JDBC Connection object, or null if not connected
     */
    Connection getConnection();

    /**
     * Returns metadata about a specific table including columns, primary keys, and foreign keys.
     * @param tableName The name of the table to get metadata for
     * @return TableMetaData containing all table information
     * @throws SQLException if a database access error occurs
     */
    default TableMetaData getTableMetaData(String tableName) throws SQLException {
        Connection conn = getConnection();
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection is not established or is closed.");
        }

        TableMetaData metadata = new TableMetaData(tableName);
        DatabaseMetaData dbMeta = conn.getMetaData();

        // Get column information
        try (ResultSet columns = dbMeta.getColumns(null, null, tableName, null)) {
            while (columns.next()) {
                TableMetaData.ColumnInfo col = new TableMetaData.ColumnInfo(columns.getString("COLUMN_NAME"));
                col.setTypeName(columns.getString("TYPE_NAME"));
                col.setDataType(columns.getInt("DATA_TYPE"));
                col.setSize(columns.getInt("COLUMN_SIZE"));
                col.setDecimalDigits(columns.getInt("DECIMAL_DIGITS"));
                col.setNullable("YES".equals(columns.getString("IS_NULLABLE")));
                col.setDefaultValue(columns.getString("COLUMN_DEF"));
                col.setOrdinalPosition(columns.getInt("ORDINAL_POSITION"));
                col.setRemarks(columns.getString("REMARKS"));
                try {
                    col.setAutoIncrement("YES".equals(columns.getString("IS_AUTOINCREMENT")));
                } catch (SQLException ignored) {
                    // Some databases don't support IS_AUTOINCREMENT
                }
                metadata.getColumns().add(col);
            }
        }

        // Get primary key information
        try (ResultSet pks = dbMeta.getPrimaryKeys(null, null, tableName)) {
            while (pks.next()) {
                TableMetaData.PrimaryKeyInfo pk = new TableMetaData.PrimaryKeyInfo(pks.getString("COLUMN_NAME"));
                pk.setPkName(pks.getString("PK_NAME"));
                pk.setKeySeq(pks.getInt("KEY_SEQ"));
                metadata.getPrimaryKeys().add(pk);
            }
        }

        // Get foreign key information
        try (ResultSet fks = dbMeta.getImportedKeys(null, null, tableName)) {
            while (fks.next()) {
                TableMetaData.ForeignKeyInfo fk = new TableMetaData.ForeignKeyInfo(fks.getString("FKCOLUMN_NAME"));
                fk.setFkName(fks.getString("FK_NAME"));
                fk.setPkTableName(fks.getString("PKTABLE_NAME"));
                fk.setPkColumnName(fks.getString("PKCOLUMN_NAME"));
                fk.setKeySeq(fks.getInt("KEY_SEQ"));
                fk.setUpdateRule(fks.getInt("UPDATE_RULE"));
                fk.setDeleteRule(fks.getInt("DELETE_RULE"));
                metadata.getForeignKeys().add(fk);
            }
        }

        // Get index information
        try (ResultSet idxs = dbMeta.getIndexInfo(null, null, tableName, false, false)) {
            while (idxs.next()) {
                String indexName = idxs.getString("INDEX_NAME");
                if (indexName != null) {
                    TableMetaData.IndexInfo idx = new TableMetaData.IndexInfo(indexName);
                    idx.setColumnName(idxs.getString("COLUMN_NAME"));
                    idx.setNonUnique(idxs.getBoolean("NON_UNIQUE"));
                    idx.setOrdinalPosition(idxs.getInt("ORDINAL_POSITION"));
                    idx.setAscOrDesc(idxs.getString("ASC_OR_DESC"));
                    metadata.getIndexes().add(idx);
                }
            }
        }

        return metadata;
    }

}
