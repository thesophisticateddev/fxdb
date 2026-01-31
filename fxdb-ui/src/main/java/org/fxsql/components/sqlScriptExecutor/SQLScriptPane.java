package org.fxsql.components.sqlScriptExecutor;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxsql.DatabaseConnection;
import org.fxsql.components.ResultTablePagination;
import org.fxsql.services.TableInteractionService;
import org.fxsql.utils.SQLSanitizer;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pane for executing SQL scripts with syntax highlighting, results display, and pagination.
 */
public class SQLScriptPane extends VBox {

    private static final Logger logger = Logger.getLogger(SQLScriptPane.class.getName());
    private static final int MAX_ROWS_PER_QUERY = 10000; // Safety limit

    private final SQLEditorToolBar toolBar;
    private final SQLEditor editor;
    private final TabPane resultsTabPane;
    private final TextArea statusArea;
    private final ExecutorService executorService;
    private final ProgressIndicator progressIndicator;
    private final SplitPane splitPane;
    private DatabaseConnection connection;

    public SQLScriptPane(DatabaseConnection connection) {
        super();
        this.connection = connection;
        this.executorService = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SQL-Executor");
            t.setDaemon(true);
            return t;
        });

        // Initialize UI components
        toolBar = new SQLEditorToolBar();
        editor = new SQLEditor();
        resultsTabPane = new TabPane();
        statusArea = new TextArea();
        progressIndicator = new ProgressIndicator();
        splitPane = new SplitPane();

        setupUI();
        setupEventHandlers();
    }

    private void setupUI() {
        // Configure editor - wrap in a VBox to control sizing
        VBox editorContainer = new VBox(editor);
        VBox.setVgrow(editor, Priority.ALWAYS);
        editorContainer.setMinHeight(100);
        editorContainer.setPrefHeight(200);

        // Configure status area
        statusArea.setEditable(false);
        statusArea.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 12px;");
        statusArea.setWrapText(true);

        // Configure progress indicator
        progressIndicator.setVisible(false);
        progressIndicator.setMaxSize(30, 30);
        progressIndicator.setPadding(new Insets(5));

        // Configure results tab pane
        resultsTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        resultsTabPane.setMinHeight(150);

        // Add status tab
        Tab statusTab = new Tab("Messages", statusArea);
        statusTab.setClosable(false);
        resultsTabPane.getTabs().add(statusTab);

        // Create split pane with editor on top and results on bottom
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(editorContainer, resultsTabPane);
        splitPane.setDividerPositions(0.4); // 40% editor, 60% results
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // Layout - toolbar at top, then split pane
        this.getChildren().addAll(toolBar, progressIndicator, splitPane);
        this.setSpacing(2);
        this.setPadding(new Insets(5));
    }

    private void setupEventHandlers() {
        // Execute all queries button
        Button executeScriptBtn = toolBar.getExecuteScript();
        executeScriptBtn.setOnMouseClicked(this::executeScriptOnBtnAction);

        // Execute selection button (if available)
        Button executeSelectionBtn = toolBar.getExecuteSelection();
        if (executeSelectionBtn != null) {
            executeSelectionBtn.setOnMouseClicked(this::executeSelectionOnBtnAction);
        }
    }

    private void executeSelectionOnBtnAction(MouseEvent event) {
        String selectedText = editor.getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            appendStatus("No text selected. Please select SQL to execute.\n");
            return;
        }

        String[] queries = {selectedText.trim()};
        executeQueries(queries);
    }

    private void executeScriptOnBtnAction(MouseEvent mouseEvent) {
        String[] queries = editor.sqlQueriesInEditor();
        if (queries == null || queries.length == 0) {
            logger.warning("No queries found for execution");
            appendStatus("No queries found for execution.\n");
            return;
        }

        executeQueries(queries);
    }

    private void executeQueries(String[] queries) {
        // Clear previous results (keep status tab)
        resultsTabPane.getTabs().removeIf(tab -> !tab.getText().equals("Messages"));
        statusArea.clear();

        appendStatus("═══════════════════════════════════════════════════════\n");
        appendStatus("Executing " + queries.length + " query(ies)...\n");
        appendStatus("═══════════════════════════════════════════════════════\n\n");

        // Validate and sanitize queries
        List<String> validatedQueries = new ArrayList<>();
        for (int i = 0; i < queries.length; i++) {
            String query = queries[i].trim();
            if (query.isEmpty()) continue;

            SQLSanitizer.ValidationResult validation = SQLSanitizer.validateQuery(query, false);

            if (!validation.isValid()) {
                appendStatus(String.format("Query %d: BLOCKED - %s\n", i + 1, validation.getMessage()));
                appendStatus("  SQL: " + truncateQuery(query) + "\n\n");
                continue;
            }

            if (validation.hasWarning()) {
                // Show confirmation dialog for dangerous operations
                boolean confirmed = showDangerousQueryConfirmation(query, validation.getMessage());
                if (!confirmed) {
                    appendStatus(String.format("Query %d: CANCELLED by user\n", i + 1));
                    appendStatus("  SQL: " + truncateQuery(query) + "\n\n");
                    continue;
                }
            }

            validatedQueries.add(validation.getSanitizedQuery());
        }

        if (validatedQueries.isEmpty()) {
            appendStatus("No valid queries to execute.\n");
            return;
        }

        // Execute validated queries asynchronously
        executeQueriesAsync(validatedQueries.toArray(new String[0]));
    }

    private boolean showDangerousQueryConfirmation(String query, String warning) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Dangerous Query");
        alert.setHeaderText(warning);
        alert.setContentText("Query: " + truncateQuery(query) + "\n\nDo you want to proceed?");

        ButtonType proceedButton = new ButtonType("Proceed", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(proceedButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == proceedButton;
    }

    private void executeQueriesAsync(String[] queries) {
        Task<List<QueryResult>> executionTask = new Task<>() {
            @Override
            protected List<QueryResult> call() throws Exception {
                List<QueryResult> results = new ArrayList<>();

                for (int i = 0; i < queries.length; i++) {
                    String query = queries[i];
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
            if (toolBar.getExecuteSelection() != null) {
                toolBar.getExecuteSelection().setDisable(true);
            }
        });

        executionTask.setOnSucceeded(event -> {
            progressIndicator.setVisible(false);
            toolBar.getExecuteScript().setDisable(false);
            if (toolBar.getExecuteSelection() != null) {
                toolBar.getExecuteSelection().setDisable(false);
            }

            List<QueryResult> results = executionTask.getValue();
            displayResults(results);
        });

        executionTask.setOnFailed(event -> {
            progressIndicator.setVisible(false);
            toolBar.getExecuteScript().setDisable(false);
            if (toolBar.getExecuteSelection() != null) {
                toolBar.getExecuteSelection().setDisable(false);
            }

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

        if (SQLSanitizer.isReadOnlyQuery(query)) {
            // Read query (SELECT, WITH, SHOW, etc.)
            ResultSet rs = connection.executeReadQuery(query);
            if (rs != null) {
                result.columns = extractColumns(rs);
                result.data = extractResultSetData(rs, MAX_ROWS_PER_QUERY);
                result.rowCount = result.data.size();
                result.isReadQuery = true;
                result.truncated = result.rowCount >= MAX_ROWS_PER_QUERY;

                // Close the result set
                try {
                    rs.close();
                } catch (SQLException ignored) {}
            } else {
                throw new SQLException("Query returned null ResultSet");
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
            String columnName = metaData.getColumnLabel(i);
            if (columnName == null || columnName.isEmpty()) {
                columnName = metaData.getColumnName(i);
            }
            columns.add(columnName);
        }

        return columns;
    }

    private List<ObservableList<Object>> extractResultSetData(ResultSet rs, int maxRows) throws SQLException {
        List<ObservableList<Object>> data = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            ObservableList<Object> row = FXCollections.observableArrayList();
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                // Convert null to displayable string
                row.add(value != null ? value : "[NULL]");
            }
            data.add(row);
            rowCount++;
        }

        return data;
    }

    private void displayResults(List<QueryResult> results) {
        int successCount = 0;
        int failureCount = 0;
        long totalTime = 0;

        for (QueryResult result : results) {
            if (result.success) {
                successCount++;
                totalTime += result.executionTime;

                appendStatus(String.format("✓ Query %d: SUCCESS (%d ms)\n",
                        result.queryNumber, result.executionTime));

                if (result.isReadQuery) {
                    String rowInfo = result.truncated
                            ? String.format("  Returned %d row(s) (truncated, max %d)", result.rowCount, MAX_ROWS_PER_QUERY)
                            : String.format("  Returned %d row(s)", result.rowCount);
                    appendStatus(rowInfo + "\n");
                    createResultTab(result);
                } else {
                    appendStatus(String.format("  Affected %d row(s)\n", result.rowCount));
                }

                appendStatus("  SQL: " + truncateQuery(result.query) + "\n\n");
            } else {
                failureCount++;
                appendStatus(String.format("✗ Query %d: FAILED\n", result.queryNumber));
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

        appendStatus("═══════════════════════════════════════════════════════\n");
        appendStatus(String.format("Execution Complete: %d succeeded, %d failed (Total: %d ms)\n",
                successCount, failureCount, totalTime));
        appendStatus("═══════════════════════════════════════════════════════\n");
    }

    private void createResultTab(QueryResult result) {
        Platform.runLater(() -> {
            // Create table view
            TableView<ObservableList<Object>> resultTable = new TableView<>();
            resultTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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

                // Custom cell factory to handle different data types
                column.setCellFactory(tc -> new TableCell<>() {
                    @Override
                    protected void updateItem(Object item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setStyle("");
                        } else if ("[NULL]".equals(item.toString())) {
                            setText("NULL");
                            setStyle("-fx-text-fill: #888; -fx-font-style: italic;");
                        } else {
                            setText(item.toString());
                            setStyle("");
                        }
                    }
                });

                column.setMinWidth(80);
                resultTable.getColumns().add(column);
            }

            // Create pagination wrapper
            ResultTablePagination<ObservableList<Object>> paginatedTable = new ResultTablePagination<>(resultTable);
            paginatedTable.setData(result.data);

            // Create tab
            String tabTitle = String.format("Query %d (%d rows%s)",
                    result.queryNumber,
                    result.rowCount,
                    result.truncated ? "+" : "");

            Tab resultTab = new Tab(tabTitle);
            resultTab.setContent(paginatedTable);
            resultsTabPane.getTabs().add(resultTab);

            // Select the new tab
            resultsTabPane.getSelectionModel().select(resultTab);
        });
    }

    private void appendStatus(String text) {
        Platform.runLater(() -> statusArea.appendText(text));
    }

    private String truncateQuery(String query) {
        String normalized = query.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 100) {
            return normalized.substring(0, 97) + "...";
        }
        return normalized;
    }

    private void showErrorAlert(String title, String header, String content, Throwable exception) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(truncateQuery(content));

            // Add exception details in expandable area
            if (exception != null) {
                TextArea textArea = new TextArea(getStackTraceAsString(exception));
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setMaxHeight(Double.MAX_VALUE);
                textArea.setStyle("-fx-font-family: monospace;");

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
        if (exception.getCause() != null) {
            sb.append("\nCaused by: ").append(exception.getCause().toString()).append("\n");
        }
        return sb.toString();
    }

    public void setConnection(DatabaseConnection connection) {
        this.connection = connection;
    }

    public DatabaseConnection getConnection() {
        return this.connection;
    }

    public SQLEditor getEditor() {
        return editor;
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
        boolean truncated;
        long executionTime;
        int rowCount;
        List<String> columns;
        List<ObservableList<Object>> data;
        Throwable error;
    }
}