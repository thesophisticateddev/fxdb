package org.fxsql.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import org.fxsql.DatabaseConnection;
import org.fxsql.model.ColumnDefinition;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for creating new database tables.
 */
public class CreateTableController {

    private static final Logger logger = Logger.getLogger(CreateTableController.class.getName());

    @FXML
    private TextField tableNameField;

    @FXML
    private TableView<ColumnDefinition> columnsTable;

    @FXML
    private TableColumn<ColumnDefinition, String> columnNameCol;

    @FXML
    private TableColumn<ColumnDefinition, String> dataTypeCol;

    @FXML
    private TableColumn<ColumnDefinition, Boolean> primaryKeyCol;

    @FXML
    private TableColumn<ColumnDefinition, Boolean> notNullCol;

    @FXML
    private TableColumn<ColumnDefinition, Boolean> uniqueCol;

    @FXML
    private TableColumn<ColumnDefinition, String> defaultValueCol;

    @FXML
    private TextArea sqlPreviewArea;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private Button createButton;

    private DatabaseConnection databaseConnection;
    private final ObservableList<ColumnDefinition> columns = FXCollections.observableArrayList();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Common SQL data types
    private static final ObservableList<String> DATA_TYPES = FXCollections.observableArrayList(
            "INTEGER",
            "BIGINT",
            "SMALLINT",
            "DECIMAL(10,2)",
            "FLOAT",
            "DOUBLE",
            "BOOLEAN",
            "VARCHAR(50)",
            "VARCHAR(100)",
            "VARCHAR(255)",
            "TEXT",
            "CHAR(10)",
            "DATE",
            "TIME",
            "TIMESTAMP",
            "DATETIME",
            "BLOB",
            "JSON",
            "UUID"
    );

    private Runnable onTableCreated;

    @FXML
    public void initialize() {
        setupColumnsTable();
        setupPreviewListener();

        // Add initial column
        columns.add(new ColumnDefinition("id", "INTEGER"));
        columns.get(0).setPrimaryKey(true);

        updateSqlPreview();
    }

    private void setupColumnsTable() {
        columnsTable.setEditable(true);
        columnsTable.setItems(columns);

        // Column Name
        columnNameCol.setCellValueFactory(cellData -> cellData.getValue().columnNameProperty());
        columnNameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        columnNameCol.setOnEditCommit(event -> {
            event.getRowValue().setColumnName(event.getNewValue());
            updateSqlPreview();
        });

        // Data Type - ComboBox
        dataTypeCol.setCellValueFactory(cellData -> cellData.getValue().dataTypeProperty());
        dataTypeCol.setCellFactory(ComboBoxTableCell.forTableColumn(DATA_TYPES));
        dataTypeCol.setOnEditCommit(event -> {
            event.getRowValue().setDataType(event.getNewValue());
            updateSqlPreview();
        });

        // Primary Key - CheckBox
        primaryKeyCol.setCellValueFactory(cellData -> cellData.getValue().primaryKeyProperty());
        primaryKeyCol.setCellFactory(CheckBoxTableCell.forTableColumn(primaryKeyCol));

        // Not Null - CheckBox
        notNullCol.setCellValueFactory(cellData -> cellData.getValue().notNullProperty());
        notNullCol.setCellFactory(CheckBoxTableCell.forTableColumn(notNullCol));

        // Unique - CheckBox
        uniqueCol.setCellValueFactory(cellData -> cellData.getValue().uniqueProperty());
        uniqueCol.setCellFactory(CheckBoxTableCell.forTableColumn(uniqueCol));

        // Default Value
        defaultValueCol.setCellValueFactory(cellData -> cellData.getValue().defaultValueProperty());
        defaultValueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        defaultValueCol.setOnEditCommit(event -> {
            event.getRowValue().setDefaultValue(event.getNewValue());
            updateSqlPreview();
        });

        // Listen to checkbox changes
        columns.addListener((javafx.collections.ListChangeListener<ColumnDefinition>) c -> updateSqlPreview());
    }

    private void setupPreviewListener() {
        tableNameField.textProperty().addListener((obs, oldVal, newVal) -> updateSqlPreview());
    }

    @FXML
    private void onAddColumn() {
        ColumnDefinition newColumn = new ColumnDefinition("new_column", "VARCHAR(255)");

        // Add listeners for checkbox properties
        newColumn.primaryKeyProperty().addListener((obs, oldVal, newVal) -> updateSqlPreview());
        newColumn.notNullProperty().addListener((obs, oldVal, newVal) -> updateSqlPreview());
        newColumn.uniqueProperty().addListener((obs, oldVal, newVal) -> updateSqlPreview());

        columns.add(newColumn);
        updateSqlPreview();
    }

    @FXML
    private void onRemoveColumn() {
        ColumnDefinition selected = columnsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            columns.remove(selected);
            updateSqlPreview();
        } else if (!columns.isEmpty()) {
            columns.remove(columns.size() - 1);
            updateSqlPreview();
        }
    }

    @FXML
    private void onCreateTable() {
        String tableName = tableNameField.getText();
        if (tableName == null || tableName.trim().isEmpty()) {
            showError("Please enter a table name.");
            return;
        }

        if (columns.isEmpty()) {
            showError("Please add at least one column.");
            return;
        }

        // Validate columns
        for (ColumnDefinition col : columns) {
            if (!col.isValid()) {
                showError("Invalid column definition. Please check all columns have names and types.");
                return;
            }
        }

        String sql = generateCreateTableSql();
        if (sql == null || sql.isEmpty()) {
            showError("Failed to generate SQL.");
            return;
        }

        executeCreateTable(sql);
    }

    private void executeCreateTable(String sql) {
        if (databaseConnection == null || !databaseConnection.isConnected()) {
            showError("No active database connection.");
            return;
        }

        setLoading(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                databaseConnection.executeWriteQuery(sql);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            setLoading(false);
            statusLabel.setText("Table created successfully!");
            statusLabel.setStyle("-fx-text-fill: green;");
            logger.info("Table created: " + tableNameField.getText());

            // Notify parent to refresh
            if (onTableCreated != null) {
                onTableCreated.run();
            }

            // Close window after short delay
            Platform.runLater(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                closeWindow();
            });
        });

        task.setOnFailed(event -> {
            setLoading(false);
            Throwable ex = task.getException();
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            showError("Failed to create table: " + errorMsg);
            logger.log(Level.SEVERE, "Failed to create table", ex);
        });

        executor.submit(task);
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) tableNameField.getScene().getWindow();
        stage.close();
        executor.shutdown();
    }

    private void updateSqlPreview() {
        String sql = generateCreateTableSql();
        sqlPreviewArea.setText(sql);
    }

    private String generateCreateTableSql() {
        String tableName = tableNameField.getText();
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = "new_table";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(sanitizeIdentifier(tableName)).append(" (\n");

        for (int i = 0; i < columns.size(); i++) {
            ColumnDefinition col = columns.get(i);
            sb.append("    ").append(col.toSqlDefinition());
            if (i < columns.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append(");");
        return sb.toString();
    }

    private String sanitizeIdentifier(String identifier) {
        // Remove any characters that aren't alphanumeric or underscore
        return identifier.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        createButton.setDisable(loading);
        tableNameField.setDisable(loading);
        columnsTable.setDisable(loading);
    }

    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
    }

    public void setOnTableCreated(Runnable callback) {
        this.onTableCreated = callback;
    }
}