package org.fxsql.services;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.fxsql.DatabaseConnection;
import org.fxsql.components.ResultTablePagination;
import org.fxsql.components.alerts.StackTraceAlert;
import org.fxsql.utils.SQLSanitizer;
import tech.tablesaw.api.Table;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for loading and interacting with database table data.
 * Supports pagination and safe query execution.
 */
public class TableInteractionService {

    private static final Logger logger = Logger.getLogger(TableInteractionService.class.getName());
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_ROWS = 10000;

    private final TableView<ObservableList<Object>> tableView;
    private ResultTablePagination<ObservableList<Object>> paginatedView;

    public TableInteractionService(TableView<ObservableList<Object>> tv) {
        this.tableView = tv;
    }

    /**
     * Sets a paginated view wrapper for the table.
     */
    public void setPaginatedView(ResultTablePagination<ObservableList<Object>> paginatedView) {
        this.paginatedView = paginatedView;
    }

    /**
     * Loads data from a table with pagination support.
     *
     * @param connection The database connection
     * @param tableName The table name to load data from
     */
    public void loadTableData(DatabaseConnection connection, String tableName) {
        // Validate table name to prevent SQL injection
        if (!SQLSanitizer.isValidIdentifier(tableName)) {
            logger.warning("Invalid table name: " + tableName);
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Invalid Table Name");
                alert.setHeaderText("Cannot load table");
                alert.setContentText("The table name contains invalid characters.");
                alert.show();
            });
            return;
        }

        String query = "SELECT * FROM " + tableName + " LIMIT " + MAX_ROWS;
        loadDataAsync(connection, query, tableName);
    }

    /**
     * Loads data from a table with offset and limit for server-side pagination.
     *
     * @param connection The database connection
     * @param tableName The table name
     * @param offset The starting row offset
     * @param limit The number of rows to fetch
     */
    public void loadTableDataPaged(DatabaseConnection connection, String tableName, int offset, int limit) {
        if (!SQLSanitizer.isValidIdentifier(tableName)) {
            logger.warning("Invalid table name: " + tableName);
            return;
        }

        String query = String.format("SELECT * FROM %s LIMIT %d OFFSET %d", tableName, limit, offset);
        loadDataAsync(connection, query, tableName);
    }

    /**
     * Executes a custom query and loads results into the table view.
     *
     * @param connection The database connection
     * @param query The SQL query to execute
     */
    public void loadDataInTableView(DatabaseConnection connection, String query) {
        // Validate query
        SQLSanitizer.ValidationResult validation = SQLSanitizer.validateQuery(query, false);
        if (!validation.isValid()) {
            logger.warning("Invalid query: " + validation.getMessage());
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Invalid Query");
                alert.setHeaderText("Cannot execute query");
                alert.setContentText(validation.getMessage());
                alert.show();
            });
            return;
        }

        if (!SQLSanitizer.isReadOnlyQuery(query)) {
            logger.warning("Only SELECT queries are allowed in table view");
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Invalid Query Type");
                alert.setHeaderText("Only SELECT queries allowed");
                alert.setContentText("Please use the SQL Script pane for modification queries.");
                alert.show();
            });
            return;
        }

        loadDataAsync(connection, validation.getSanitizedQuery(), "query");
    }

    private void loadDataAsync(DatabaseConnection connection, String query, String sourceName) {
        CompletableFuture.supplyAsync(() -> {
            try {
                ResultSet rs = connection.executeReadQuery(query);
                if (rs == null) {
                    logger.warning("No data found from: " + sourceName);
                    return null;
                }

                // Extract data directly from ResultSet
                List<String> columns = new ArrayList<>();
                List<ObservableList<Object>> rows = new ArrayList<>();

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Get column names
                for (int i = 1; i <= columnCount; i++) {
                    String colName = metaData.getColumnLabel(i);
                    if (colName == null || colName.isEmpty()) {
                        colName = metaData.getColumnName(i);
                    }
                    columns.add(colName);
                }

                // Get row data
                while (rs.next()) {
                    ObservableList<Object> row = FXCollections.observableArrayList();
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = rs.getObject(i);
                        row.add(value != null ? value : "[NULL]");
                    }
                    rows.add(row);
                }

                rs.close();
                return new TableData(columns, rows);

            } catch (SQLException e) {
                throw new RuntimeException("Failed to execute query: " + e.getMessage(), e);
            }
        }).thenAcceptAsync(tableData -> {
            if (tableData != null) {
                updateTableView(tableData);
            }
        }, Platform::runLater).exceptionally(throwable -> {
            logger.log(Level.SEVERE, "Failed to load data", throwable);

            Platform.runLater(() -> {
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                StackTraceAlert alert = new StackTraceAlert(
                        Alert.AlertType.ERROR,
                        "Failed to load data",
                        "SQL query exception",
                        "Could not load data: " + cause.getMessage(),
                        (Exception) cause
                );
                alert.show();
            });
            return null;
        });
    }

    private void updateTableView(TableData tableData) {
        tableView.getColumns().clear();
        tableView.getItems().clear();

        // Create columns dynamically
        for (int i = 0; i < tableData.columns.size(); i++) {
            final int colIndex = i;
            TableColumn<ObservableList<Object>, Object> column =
                    new TableColumn<>(tableData.columns.get(i));

            column.setCellValueFactory(param -> {
                ObservableList<Object> row = param.getValue();
                if (colIndex < row.size()) {
                    return new javafx.beans.property.SimpleObjectProperty<>(row.get(colIndex));
                }
                return new javafx.beans.property.SimpleObjectProperty<>(null);
            });

            column.setMinWidth(80);
            tableView.getColumns().add(column);
        }

        // Use pagination if available
        if (paginatedView != null) {
            paginatedView.setData(tableData.rows);
        } else {
            tableView.setItems(FXCollections.observableArrayList(tableData.rows));
        }
    }

    /**
     * Gets the row count for a table.
     *
     * @param connection The database connection
     * @param tableName The table name
     * @return CompletableFuture with the row count
     */
    public CompletableFuture<Integer> getTableRowCount(DatabaseConnection connection, String tableName) {
        if (!SQLSanitizer.isValidIdentifier(tableName)) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = "SELECT COUNT(*) FROM " + tableName;
                ResultSet rs = connection.executeReadQuery(query);
                if (rs != null && rs.next()) {
                    int count = rs.getInt(1);
                    rs.close();
                    return count;
                }
                return 0;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get row count", e);
                return 0;
            }
        });
    }

    /**
     * Clears the table view.
     */
    public void clear() {
        Platform.runLater(() -> {
            tableView.getColumns().clear();
            tableView.getItems().clear();
            if (paginatedView != null) {
                paginatedView.clearData();
            }
        });
    }

    // Helper class to hold table data
    private static class TableData {
        final List<String> columns;
        final List<ObservableList<Object>> rows;

        TableData(List<String> columns, List<ObservableList<Object>> rows) {
            this.columns = columns;
            this.rows = rows;
        }
    }
}