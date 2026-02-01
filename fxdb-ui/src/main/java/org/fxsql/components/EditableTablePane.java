package org.fxsql.components;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.fxsql.DatabaseConnection;
import org.fxsql.model.RowChange;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An editable table pane with pagination, row editing, and CRUD operations.
 */
public class EditableTablePane extends VBox {

    private static final Logger logger = Logger.getLogger(EditableTablePane.class.getName());
    private static final int[] PAGE_SIZE_OPTIONS = {25, 50, 100, 200, 500};
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_ROWS = 10000;

    // UI Components
    private final TableView<ObservableList<Object>> tableView;
    private final HBox toolbar;
    private final HBox paginationBar;
    private final Label statusLabel;
    private final Label totalRowsLabel;
    private final Label pageInfoLabel;
    private final ComboBox<Integer> pageSizeCombo;
    private final TextField pageInput;
    private final Button saveButton;
    private final Button discardButton;
    private final Button addRowButton;
    private final Button deleteRowButton;
    private final Button refreshButton;
    private final ProgressIndicator progressIndicator;
    private final ConnectionStatusIndicator connectionStatusIndicator;

    // Navigation buttons
    private final Button firstButton;
    private final Button prevButton;
    private final Button nextButton;
    private final Button lastButton;

    // Data
    private ObservableList<ObservableList<Object>> allData = FXCollections.observableArrayList();
    private List<String> columnNames = new ArrayList<>();
    private List<Integer> columnTypes = new ArrayList<>();
    private String currentTableName;
    private DatabaseConnection databaseConnection;
    private int primaryKeyIndex = -1;

    // Pagination state
    private int currentPage = 1;
    private int pageSize = DEFAULT_PAGE_SIZE;
    private int totalPages = 1;
    private int totalRows = 0;

    // Change tracking
    private final Map<ObservableList<Object>, RowChange> pendingChanges = new LinkedHashMap<>();
    private final Set<ObservableList<Object>> newRows = new HashSet<>();
    private final Set<ObservableList<Object>> deletedRows = new HashSet<>();
    private final Set<ObservableList<Object>> modifiedRows = new HashSet<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public EditableTablePane() {
        this.tableView = new TableView<>();
        this.toolbar = new HBox(10);
        this.paginationBar = new HBox(10);
        this.statusLabel = new Label();
        this.totalRowsLabel = new Label();
        this.pageInfoLabel = new Label();
        this.pageSizeCombo = new ComboBox<>();
        this.pageInput = new TextField();
        this.progressIndicator = new ProgressIndicator();
        this.connectionStatusIndicator = new ConnectionStatusIndicator();

        // Create buttons
        this.saveButton = createButton("Save", Feather.SAVE, "Save all changes");
        this.discardButton = createButton("Discard", Feather.X_CIRCLE, "Discard all changes");
        this.addRowButton = createButton("Add Row", Feather.PLUS, "Add a new row");
        this.deleteRowButton = createButton("Delete Row", Feather.TRASH_2, "Delete selected row");
        this.refreshButton = createButton("Refresh", Feather.REFRESH_CW, "Reload data");

        this.firstButton = createIconButton(Feather.CHEVRONS_LEFT, "First page");
        this.prevButton = createIconButton(Feather.CHEVRON_LEFT, "Previous page");
        this.nextButton = createIconButton(Feather.CHEVRON_RIGHT, "Next page");
        this.lastButton = createIconButton(Feather.CHEVRONS_RIGHT, "Last page");

        setupUI();
        setupEventHandlers();
        updateButtonStates();
    }

    private void setupUI() {
        // Configure table
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tableView.setPlaceholder(new Label("No data to display. Select a table to load data."));

        // Toolbar
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(5, 10, 5, 10));
        toolbar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");

        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        deleteRowButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");

        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                connectionStatusIndicator,
                new Separator(),
                addRowButton,
                deleteRowButton,
                new Separator(),
                saveButton,
                discardButton,
                spacer,
                progressIndicator,
                statusLabel,
                new Separator(),
                refreshButton
        );

        // Pagination bar
        pageSizeCombo.getItems().addAll(PAGE_SIZE_OPTIONS[0], PAGE_SIZE_OPTIONS[1],
                PAGE_SIZE_OPTIONS[2], PAGE_SIZE_OPTIONS[3], PAGE_SIZE_OPTIONS[4]);
        pageSizeCombo.setValue(DEFAULT_PAGE_SIZE);
        pageSizeCombo.setPrefWidth(80);

        pageInput.setPrefWidth(50);
        pageInput.setAlignment(Pos.CENTER);

        paginationBar.setAlignment(Pos.CENTER_LEFT);
        paginationBar.setPadding(new Insets(5, 10, 5, 10));
        paginationBar.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        Label rowsLabel = new Label("Rows per page:");
        rowsLabel.setStyle("-fx-font-size: 12px;");

        Region paginationSpacer = new Region();
        HBox.setHgrow(paginationSpacer, Priority.ALWAYS);

        paginationBar.getChildren().addAll(
                totalRowsLabel,
                paginationSpacer,
                rowsLabel,
                pageSizeCombo,
                new Separator(),
                firstButton,
                prevButton,
                new Label("Page"),
                pageInput,
                new Label("of"),
                pageInfoLabel,
                nextButton,
                lastButton
        );

        totalRowsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        pageInfoLabel.setStyle("-fx-font-size: 12px;");

        // Layout
        VBox.setVgrow(tableView, Priority.ALWAYS);
        this.getChildren().addAll(toolbar, tableView, paginationBar);
        this.setSpacing(0);
    }

    private void setupEventHandlers() {
        // Pagination
        pageSizeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                pageSize = newVal;
                currentPage = 1;
                updateTableData();
            }
        });

        firstButton.setOnAction(e -> goToPage(1));
        prevButton.setOnAction(e -> goToPage(currentPage - 1));
        nextButton.setOnAction(e -> goToPage(currentPage + 1));
        lastButton.setOnAction(e -> goToPage(totalPages));

        pageInput.setOnAction(e -> {
            try {
                int page = Integer.parseInt(pageInput.getText());
                goToPage(page);
            } catch (NumberFormatException ex) {
                pageInput.setText(String.valueOf(currentPage));
            }
        });

        // CRUD operations
        addRowButton.setOnAction(e -> addNewRow());
        deleteRowButton.setOnAction(e -> deleteSelectedRow());
        saveButton.setOnAction(e -> saveChanges());
        discardButton.setOnAction(e -> discardChanges());
        refreshButton.setOnAction(e -> refreshData());

        // Selection change
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            deleteRowButton.setDisable(newVal == null);
        });
    }

    private Button createButton(String text, Feather icon, String tooltip) {
        Button button = new Button(text);
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(14);
        button.setGraphic(fontIcon);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private Button createIconButton(Feather icon, String tooltip) {
        Button button = new Button();
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(14);
        button.setGraphic(fontIcon);
        button.setTooltip(new Tooltip(tooltip));
        button.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #e0e0e0; -fx-cursor: hand;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));
        return button;
    }

    /**
     * Loads data from a table.
     */
    public void loadTableData(DatabaseConnection connection, String tableName) {
        this.databaseConnection = connection;
        this.currentTableName = tableName;

        // Update connection status indicator
        connectionStatusIndicator.setConnection(connection, tableName);

        if (connection == null || !connection.isConnected()) {
            showError("No active database connection");
            connectionStatusIndicator.setStatus(ConnectionStatusIndicator.Status.DISCONNECTED);
            return;
        }

        setLoading(true);
        statusLabel.setText("Loading...");

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String query = "SELECT * FROM " + tableName + " LIMIT " + MAX_ROWS;
                ResultSet rs = connection.executeReadQuery(query);

                if (rs == null) {
                    throw new SQLException("No result returned");
                }

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Get column info
                List<String> cols = new ArrayList<>();
                List<Integer> types = new ArrayList<>();
                int pkIndex = -1;

                for (int i = 1; i <= columnCount; i++) {
                    String colName = metaData.getColumnLabel(i);
                    if (colName == null || colName.isEmpty()) {
                        colName = metaData.getColumnName(i);
                    }
                    cols.add(colName);
                    types.add(metaData.getColumnType(i));

                    // Try to detect primary key (simple heuristic)
                    if (pkIndex < 0 && (colName.equalsIgnoreCase("id") ||
                            colName.toLowerCase().endsWith("_id") ||
                            colName.toLowerCase().endsWith("id"))) {
                        pkIndex = i - 1;
                    }
                }

                // Get row data
                List<ObservableList<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    ObservableList<Object> row = FXCollections.observableArrayList();
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = rs.getObject(i);
                        row.add(value);
                    }
                    rows.add(row);
                }

                rs.close();

                final List<String> finalCols = cols;
                final List<Integer> finalTypes = types;
                final int finalPkIndex = pkIndex;
                final List<ObservableList<Object>> finalRows = rows;

                Platform.runLater(() -> {
                    columnNames = finalCols;
                    columnTypes = finalTypes;
                    primaryKeyIndex = finalPkIndex;
                    allData = FXCollections.observableArrayList(finalRows);
                    totalRows = finalRows.size();

                    setupColumns();
                    calculateTotalPages();
                    currentPage = 1;
                    updateTableData();
                    updateButtonStates();
                    updateLabels();
                    clearPendingChanges();

                    setLoading(false);
                    statusLabel.setText("Loaded " + totalRows + " rows from " + tableName);
                    connectionStatusIndicator.setStatus(ConnectionStatusIndicator.Status.CONNECTED);
                });

                return null;
            }
        };

        loadTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                setLoading(false);
                Throwable ex = loadTask.getException();
                showError("Failed to load data: " + (ex != null ? ex.getMessage() : "Unknown error"));
                connectionStatusIndicator.setStatus(ConnectionStatusIndicator.Status.ERROR);
                logger.log(Level.SEVERE, "Failed to load table data", ex);
            });
        });

        executor.submit(loadTask);
    }

    private void setupColumns() {
        tableView.getColumns().clear();

        for (int i = 0; i < columnNames.size(); i++) {
            final int colIndex = i;
            TableColumn<ObservableList<Object>, Object> column = new TableColumn<>(columnNames.get(i));

            // Cell value factory
            column.setCellValueFactory(param -> {
                ObservableList<Object> row = param.getValue();
                if (colIndex < row.size()) {
                    return new SimpleObjectProperty<>(row.get(colIndex));
                }
                return new SimpleObjectProperty<>(null);
            });

            // Make cells editable
            column.setCellFactory(col -> new EditableTableCell(colIndex));

            // Handle edit commit
            column.setOnEditCommit(event -> {
                ObservableList<Object> row = event.getRowValue();
                Object oldValue = row.get(colIndex);
                Object newValue = event.getNewValue();

                if (!Objects.equals(oldValue, newValue)) {
                    row.set(colIndex, newValue);
                    markRowAsModified(row, colIndex, oldValue, newValue);
                }
            });

            column.setMinWidth(80);
            column.setEditable(true);
            tableView.getColumns().add(column);
        }
    }

    private void markRowAsModified(ObservableList<Object> row, int colIndex, Object oldValue, Object newValue) {
        if (newRows.contains(row)) {
            // New rows don't need change tracking - they'll be inserted
            return;
        }

        modifiedRows.add(row);

        RowChange change = pendingChanges.get(row);
        if (change == null) {
            // Find original row data
            ObservableList<Object> originalRow = FXCollections.observableArrayList(row);
            originalRow.set(colIndex, oldValue);

            change = new RowChange(RowChange.ChangeType.UPDATE, currentTableName,
                    originalRow, row, columnNames, primaryKeyIndex);
            pendingChanges.put(row, change);
        }
        change.addColumnChange(colIndex, newValue);

        updateButtonStates();
        statusLabel.setText("Pending changes: " + getTotalPendingChanges());
    }

    private void addNewRow() {
        ObservableList<Object> newRow = FXCollections.observableArrayList();
        for (int i = 0; i < columnNames.size(); i++) {
            newRow.add(null);
        }

        allData.add(0, newRow);
        newRows.add(newRow);
        totalRows++;

        RowChange change = new RowChange(RowChange.ChangeType.INSERT, currentTableName,
                null, newRow, columnNames, primaryKeyIndex);
        pendingChanges.put(newRow, change);

        calculateTotalPages();
        currentPage = 1;
        updateTableData();
        updateLabels();
        updateButtonStates();

        // Select the new row for editing
        tableView.getSelectionModel().select(newRow);
        if (!tableView.getColumns().isEmpty()) {
            tableView.edit(0, tableView.getColumns().get(0));
        }

        statusLabel.setText("New row added. Fill in values and save.");
    }

    private void deleteSelectedRow() {
        ObservableList<Object> selectedRow = tableView.getSelectionModel().getSelectedItem();
        if (selectedRow == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete this row?");
        confirm.setContentText("This will mark the row for deletion. Click Save to confirm.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (newRows.contains(selectedRow)) {
                // Remove unsaved new row immediately
                allData.remove(selectedRow);
                newRows.remove(selectedRow);
                pendingChanges.remove(selectedRow);
                totalRows--;
            } else {
                // Mark existing row for deletion
                deletedRows.add(selectedRow);
                modifiedRows.remove(selectedRow);

                RowChange change = new RowChange(RowChange.ChangeType.DELETE, currentTableName,
                        selectedRow, null, columnNames, primaryKeyIndex);
                pendingChanges.put(selectedRow, change);

                // Visual indication
                // The row will be hidden after save
            }

            calculateTotalPages();
            updateTableData();
            updateLabels();
            updateButtonStates();
            statusLabel.setText("Row marked for deletion. Click Save to confirm.");
        }
    }

    private void saveChanges() {
        if (pendingChanges.isEmpty()) {
            statusLabel.setText("No changes to save");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Save");
        confirm.setHeaderText("Save " + getTotalPendingChanges() + " change(s)?");
        confirm.setContentText("This will execute the following SQL statements.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        setLoading(true);
        statusLabel.setText("Saving changes...");

        Task<Integer> saveTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                int successCount = 0;
                List<String> errors = new ArrayList<>();

                for (RowChange change : pendingChanges.values()) {
                    String sql = change.toSql();
                    if (sql == null || sql.isEmpty()) {
                        continue;
                    }

                    try {
                        logger.info("Executing: " + sql);
                        databaseConnection.executeWriteQuery(sql);
                        successCount++;
                    } catch (SQLException e) {
                        errors.add(e.getMessage());
                        logger.log(Level.WARNING, "Failed to execute: " + sql, e);
                    }
                }

                if (!errors.isEmpty()) {
                    throw new SQLException("Some operations failed: " + String.join("; ", errors));
                }

                return successCount;
            }
        };

        saveTask.setOnSucceeded(event -> {
            int count = saveTask.getValue();
            Platform.runLater(() -> {
                setLoading(false);
                statusLabel.setText("Saved " + count + " change(s) successfully");
                statusLabel.setStyle("-fx-text-fill: green;");

                // Refresh data
                refreshData();
            });
        });

        saveTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                setLoading(false);
                Throwable ex = saveTask.getException();
                showError("Save failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            });
        });

        executor.submit(saveTask);
    }

    private void discardChanges() {
        if (pendingChanges.isEmpty()) {
            statusLabel.setText("No changes to discard");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Discard");
        confirm.setHeaderText("Discard all changes?");
        confirm.setContentText("This will reload the original data and discard all pending changes.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            refreshData();
        }
    }

    private void refreshData() {
        if (currentTableName != null && databaseConnection != null) {
            loadTableData(databaseConnection, currentTableName);
        }
    }

    private void clearPendingChanges() {
        pendingChanges.clear();
        newRows.clear();
        deletedRows.clear();
        modifiedRows.clear();
        statusLabel.setStyle("");
    }

    private int getTotalPendingChanges() {
        return pendingChanges.size();
    }

    // Pagination methods
    private void calculateTotalPages() {
        int dataSize = allData.size() - deletedRows.size();
        totalPages = Math.max(1, (int) Math.ceil((double) dataSize / pageSize));
    }

    private void goToPage(int page) {
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        currentPage = page;
        updateTableData();
        updateLabels();
        updateButtonStates();
    }

    private void updateTableData() {
        // Filter out deleted rows
        List<ObservableList<Object>> visibleData = new ArrayList<>();
        for (ObservableList<Object> row : allData) {
            if (!deletedRows.contains(row)) {
                visibleData.add(row);
            }
        }

        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, visibleData.size());

        if (fromIndex >= visibleData.size()) {
            tableView.setItems(FXCollections.observableArrayList());
        } else {
            List<ObservableList<Object>> pageData = visibleData.subList(fromIndex, toIndex);
            tableView.setItems(FXCollections.observableArrayList(pageData));
        }
    }

    private void updateButtonStates() {
        // Pagination buttons
        firstButton.setDisable(currentPage <= 1);
        prevButton.setDisable(currentPage <= 1);
        nextButton.setDisable(currentPage >= totalPages);
        lastButton.setDisable(currentPage >= totalPages);

        // CRUD buttons
        boolean hasChanges = !pendingChanges.isEmpty();
        saveButton.setDisable(!hasChanges);
        discardButton.setDisable(!hasChanges);
        deleteRowButton.setDisable(tableView.getSelectionModel().getSelectedItem() == null);

        boolean hasConnection = databaseConnection != null && databaseConnection.isConnected();
        addRowButton.setDisable(!hasConnection || currentTableName == null);
        refreshButton.setDisable(!hasConnection || currentTableName == null);
    }

    private void updateLabels() {
        pageInfoLabel.setText(String.valueOf(totalPages));
        pageInput.setText(String.valueOf(currentPage));

        int visibleCount = totalRows - deletedRows.size();
        int fromRow = visibleCount == 0 ? 0 : (currentPage - 1) * pageSize + 1;
        int toRow = Math.min(currentPage * pageSize, visibleCount);

        totalRowsLabel.setText(String.format("Showing %d-%d of %d rows", fromRow, toRow, visibleCount));
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        tableView.setDisable(loading);
        saveButton.setDisable(loading);
        addRowButton.setDisable(loading);
        deleteRowButton.setDisable(loading);
        refreshButton.setDisable(loading);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
        logger.warning(message);
    }

    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
        updateButtonStates();
    }

    /**
     * Sets the database connection with a connection name for display.
     */
    public void setDatabaseConnection(DatabaseConnection connection, String connectionName) {
        this.databaseConnection = connection;
        connectionStatusIndicator.setConnection(connection, connectionName);
        updateButtonStates();
    }

    /**
     * Updates the connection status indicator.
     */
    public void updateConnectionStatus() {
        connectionStatusIndicator.checkConnectionHealth();
    }

    public TableView<ObservableList<Object>> getTableView() {
        return tableView;
    }

    public void shutdown() {
        executor.shutdown();
        connectionStatusIndicator.shutdown();
    }

    /**
     * Custom editable table cell that handles null values and type conversion.
     */
    private class EditableTableCell extends TableCell<ObservableList<Object>, Object> {
        private TextField textField;
        private final int columnIndex;

        public EditableTableCell(int columnIndex) {
            this.columnIndex = columnIndex;
        }

        @Override
        public void startEdit() {
            if (!isEmpty()) {
                super.startEdit();
                createTextField();
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getDisplayText(getItem()));
            setGraphic(null);
        }

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getDisplayText(item));
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getDisplayText(item));
                    setGraphic(null);

                    // Highlight modified/new/deleted rows
                    ObservableList<Object> row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) {
                        if (newRows.contains(row)) {
                            setStyle("-fx-background-color: #e8f5e9;"); // Light green
                        } else if (deletedRows.contains(row)) {
                            setStyle("-fx-background-color: #ffebee; -fx-text-fill: #999;"); // Light red
                        } else if (modifiedRows.contains(row)) {
                            setStyle("-fx-background-color: #fff3e0;"); // Light orange
                        } else {
                            setStyle("");
                        }
                    }
                }
            }
        }

        private void createTextField() {
            textField = new TextField(getDisplayText(getItem()));
            textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);

            textField.setOnAction(evt -> commitEdit(parseValue(textField.getText())));

            textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    commitEdit(parseValue(textField.getText()));
                }
            });

            textField.setOnKeyPressed(event -> {
                switch (event.getCode()) {
                    case ESCAPE:
                        cancelEdit();
                        break;
                    case TAB:
                        commitEdit(parseValue(textField.getText()));
                        // Move to next cell
                        TableColumn<ObservableList<Object>, ?> nextColumn = getNextColumn(!event.isShiftDown());
                        if (nextColumn != null) {
                            getTableView().edit(getTableRow().getIndex(), nextColumn);
                        }
                        break;
                }
            });
        }

        private TableColumn<ObservableList<Object>, ?> getNextColumn(boolean forward) {
            List<TableColumn<ObservableList<Object>, ?>> columns = getTableView().getColumns();
            if (columns.isEmpty()) return null;

            int currentIndex = columns.indexOf(getTableColumn());
            int nextIndex = forward ? currentIndex + 1 : currentIndex - 1;

            if (nextIndex >= 0 && nextIndex < columns.size()) {
                return columns.get(nextIndex);
            }
            return null;
        }

        private String getDisplayText(Object item) {
            if (item == null) {
                return "[NULL]";
            }
            return item.toString();
        }

        private Object parseValue(String text) {
            if (text == null || text.isEmpty() || "[NULL]".equalsIgnoreCase(text)) {
                return null;
            }
            return text;
        }
    }
}