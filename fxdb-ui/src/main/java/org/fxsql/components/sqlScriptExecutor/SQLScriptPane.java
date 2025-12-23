package org.fxsql.components.sqlScriptExecutor;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxsql.DatabaseConnection;
import org.fxsql.services.TableInteractionService;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SQLScriptPane extends VBox {

    private static final Logger logger = Logger.getLogger(SQLScriptPane.class.getName());
    private final SQLEditorToolBar toolBar;
    private final SQLEditor editor;
    private final TableView<ObservableList<Object>> tableView;
    private final TabPane resultsTabPane;
    private final TextArea statusArea;
    private final TableInteractionService tableInteractionService;
    private final ExecutorService executorService;
    private final ProgressIndicator progressIndicator;
    private DatabaseConnection connection;

    public SQLScriptPane(DatabaseConnection connection) {
        super();
        this.connection = connection;
        this.executorService = Executors.newFixedThreadPool(2);

        // Initialize UI components
        toolBar = new SQLEditorToolBar();
        editor = new SQLEditor();
        tableView = new TableView<>();
        resultsTabPane = new TabPane();
        statusArea = new TextArea();
        progressIndicator = new ProgressIndicator();
        tableInteractionService = new TableInteractionService(tableView);

        // Configure status area
        statusArea.setEditable(false);
        statusArea.setPrefHeight(100);
        statusArea.setStyle("-fx-font-family: monospace;");

        // Configure progress indicator
        progressIndicator.setVisible(false);
        progressIndicator.setMaxSize(50, 50);

        // Configure results tab pane
        resultsTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        VBox.setVgrow(resultsTabPane, Priority.ALWAYS);

        // Add status tab
        Tab statusTab = new Tab("Status", statusArea);
        statusTab.setClosable(false);
        resultsTabPane.getTabs().add(statusTab);

        // Layout
        this.getChildren().addAll(toolBar, editor, progressIndicator, resultsTabPane);
        this.setSpacing(5);

        // Event handlers
        Button executeScriptBtn = toolBar.getExecuteScript();
        executeScriptBtn.setOnMouseClicked(this::executeScriptOnBtnAction);
    }

    private void executeScriptOnBtnAction(MouseEvent mouseEvent) {
        String[] queries = editor.sqlQueriesInEditor();
        if (queries == null || queries.length == 0) {
            logger.warning("No queries found for execution");
            appendStatus("No queries found for execution\n");
            return;
        }

        // Clear previous results
        resultsTabPane.getTabs().removeIf(tab -> !tab.getText().equals("Status"));
        statusArea.clear();

        appendStatus("Executing " + queries.length + " query(ies)...\n\n");

        // Execute queries asynchronously
        executeQueriesAsync(queries);
    }

    private void executeQueriesAsync(String[] queries) {
        Task<List<QueryResult>> executionTask = new Task<>() {
            @Override
            protected List<QueryResult> call() throws Exception {
                List<QueryResult> results = new ArrayList<>();

                for (int i = 0; i < queries.length; i++) {
                    String query = queries[i].trim();
                    if (query.isEmpty()) continue;

                    int queryNum = i + 1;
                    updateMessage("Executing query " + queryNum + " of " + queries.length);

                    try {
                        QueryResult result = executeQuery(query, queryNum);
                        results.add(result);
                    } catch (Exception e) {
                        QueryResult errorResult = new QueryResult();
                        errorResult.queryNumber = queryNum;
                        errorResult.query = query;
                        errorResult.error = e;
                        errorResult.success = false;
                        results.add(errorResult);
                    }
                }

                return results;
            }
        };

        executionTask.setOnRunning(event -> {
            progressIndicator.setVisible(true);
            toolBar.getExecuteScript().setDisable(true);
        });

        executionTask.setOnSucceeded(event -> {
            progressIndicator.setVisible(false);
            toolBar.getExecuteScript().setDisable(false);

            List<QueryResult> results = executionTask.getValue();
            displayResults(results);
        });

        executionTask.setOnFailed(event -> {
            progressIndicator.setVisible(false);
            toolBar.getExecuteScript().setDisable(false);

            Throwable e = executionTask.getException();
            logger.log(Level.SEVERE, "Failed to execute queries", e);
            appendStatus("ERROR: Failed to execute queries\n" + e.getMessage() + "\n");

            showErrorAlert("Query Execution Failed",
                    "An unexpected error occurred",
                    "Failed to execute SQL queries",
                    e);
        });

        executorService.submit(executionTask);
    }

    private QueryResult executeQuery(String query, int queryNumber) throws SQLException {
        QueryResult result = new QueryResult();
        result.queryNumber = queryNumber;
        result.query = query;

        long startTime = System.currentTimeMillis();

        // Determine if it's a SELECT query or modification query
        String queryType = query.trim().substring(0, Math.min(query.length(), 6)).toUpperCase();

        if (queryType.startsWith("SELECT") || queryType.startsWith("WITH")) {
            // Read query
            try (ResultSet rs = connection.executeReadQuery(query)) {
                if (rs != null) {
                    result.data = extractResultSetData(rs);
                    result.columns = extractColumns(rs);
                    result.rowCount = result.data.size();
                    result.isReadQuery = true;
                } else {
                    throw new SQLException("Query returned null ResultSet");
                }
            }
        } else {
            // Write query (INSERT, UPDATE, DELETE, CREATE, etc.)
            int affectedRows = connection.executeWriteQuery(query);
            result.rowCount = affectedRows;
            result.isReadQuery = false;
        }

        long endTime = System.currentTimeMillis();
        result.executionTime = endTime - startTime;
        result.success = true;

        return result;
    }

    private List<String> extractColumns(ResultSet rs) throws SQLException {
        List<String> columns = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnName(i));
        }

        return columns;
    }

    private List<ObservableList<Object>> extractResultSetData(ResultSet rs) throws SQLException {
        List<ObservableList<Object>> data = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (rs.next()) {
            ObservableList<Object> row = FXCollections.observableArrayList();
            for (int i = 1; i <= columnCount; i++) {
                row.add(rs.getObject(i));
            }
            data.add(row);
        }

        return data;
    }

    private void displayResults(List<QueryResult> results) {
        int successCount = 0;
        int failureCount = 0;

        for (QueryResult result : results) {
            if (result.success) {
                successCount++;
                appendStatus(String.format("Query %d: SUCCESS (%d ms)\n",
                        result.queryNumber, result.executionTime));

                if (result.isReadQuery) {
                    appendStatus(String.format("  Returned %d row(s)\n", result.rowCount));
                    createResultTab(result);
                } else {
                    appendStatus(String.format("  Affected %d row(s)\n", result.rowCount));
                }

                appendStatus("  SQL: " + truncateQuery(result.query) + "\n\n");
            } else {
                failureCount++;
                appendStatus(String.format("Query %d: FAILED\n", result.queryNumber));
                appendStatus("  SQL: " + truncateQuery(result.query) + "\n");
                appendStatus("  ERROR: " + result.error.getMessage() + "\n\n");

                // Show detailed error for first failure
                if (failureCount == 1) {
                    Platform.runLater(() ->
                            showErrorAlert("Query Execution Error",
                                    "Query " + result.queryNumber + " failed",
                                    result.query,
                                    result.error));
                }
            }
        }

        appendStatus(String.format("\n=== Execution Complete: %d succeeded, %d failed ===\n",
                successCount, failureCount));
    }

    private void createResultTab(QueryResult result) {
        TableView<ObservableList<Object>> resultTable = new TableView<>();

        // Create columns
        for (int i = 0; i < result.columns.size(); i++) {
            final int colIndex = i;
            TableColumn<ObservableList<Object>, Object> column =
                    new TableColumn<>(result.columns.get(i));

            column.setCellValueFactory(param -> {
                ObservableList<Object> row = param.getValue();
                if (colIndex < row.size()) {
                    return new javafx.beans.property.SimpleObjectProperty<>(row.get(colIndex));
                }
                return new javafx.beans.property.SimpleObjectProperty<>(null);
            });

            resultTable.getColumns().add(column);
        }

        // Add data
        resultTable.setItems(FXCollections.observableArrayList(result.data));

        // Create tab
        Tab resultTab = new Tab("Query " + result.queryNumber + " (" + result.rowCount + " rows)");
        resultTab.setContent(resultTable);
        resultsTabPane.getTabs().add(resultTab);

        // Select the new tab
        resultsTabPane.getSelectionModel().select(resultTab);
    }

    private void appendStatus(String text) {
        Platform.runLater(() -> statusArea.appendText(text));
    }

    private String truncateQuery(String query) {
        if (query.length() > 100) {
            return query.substring(0, 97) + "...";
        }
        return query;
    }

    private void showErrorAlert(String title, String header, String content, Throwable exception) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);

            // Add exception details in expandable area
            if (exception != null) {
                TextArea textArea = new TextArea(getStackTraceAsString(exception));
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setMaxHeight(Double.MAX_VALUE);

                alert.getDialogPane().setExpandableContent(textArea);
            }

            alert.showAndWait();
        });
    }

    private String getStackTraceAsString(Throwable exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.toString()).append("\n");
        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    public void setConnection(DatabaseConnection connection) {
        this.connection = connection;
    }

    public void shutdown() {
        executorService.shutdown();
    }

    // Helper class to store query results
    private static class QueryResult {
        int queryNumber;
        String query;
        boolean success;
        boolean isReadQuery;
        long executionTime;
        int rowCount;
        List<String> columns;
        List<ObservableList<Object>> data;
        Throwable error;
    }
}