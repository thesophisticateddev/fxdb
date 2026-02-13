package org.fxsql.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.fxsql.DatabaseConnection;
import org.fxsql.utils.SQLSanitizer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for adding a new column to an existing table via ALTER TABLE ADD COLUMN.
 */
public class AddColumnController {

    private static final Logger logger = Logger.getLogger(AddColumnController.class.getName());

    @FXML private TextField columnNameField;
    @FXML private ComboBox<String> dataTypeCombo;
    @FXML private CheckBox notNullCheck;
    @FXML private TextField defaultValueField;
    @FXML private TextArea sqlPreviewArea;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Button addButton;

    private DatabaseConnection databaseConnection;
    private String tableName;
    private Runnable onColumnAdded;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AddColumn-Executor");
        t.setDaemon(true);
        return t;
    });

    private static final String[] DATA_TYPES = {
            "INTEGER", "BIGINT", "SMALLINT", "DECIMAL(10,2)", "FLOAT", "DOUBLE",
            "BOOLEAN", "VARCHAR(50)", "VARCHAR(100)", "VARCHAR(255)", "TEXT",
            "CHAR(10)", "DATE", "TIME", "TIMESTAMP", "DATETIME", "BLOB", "JSON", "UUID"
    };

    @FXML
    public void initialize() {
        dataTypeCombo.setItems(FXCollections.observableArrayList(DATA_TYPES));
        dataTypeCombo.getSelectionModel().select("VARCHAR(255)");

        columnNameField.textProperty().addListener((obs, o, n) -> updateSqlPreview());
        dataTypeCombo.valueProperty().addListener((obs, o, n) -> updateSqlPreview());
        notNullCheck.selectedProperty().addListener((obs, o, n) -> updateSqlPreview());
        defaultValueField.textProperty().addListener((obs, o, n) -> updateSqlPreview());

        updateSqlPreview();
    }

    @FXML
    private void onAddColumn() {
        String colName = columnNameField.getText();
        if (colName == null || colName.trim().isEmpty()) {
            showError("Please enter a column name.");
            return;
        }
        if (!SQLSanitizer.isValidIdentifier(colName.trim())) {
            showError("Invalid column name. Use only letters, numbers, and underscores.");
            return;
        }

        String dataType = dataTypeCombo.getValue();
        if (dataType == null || dataType.trim().isEmpty()) {
            showError("Please select or enter a data type.");
            return;
        }

        String sql = generateSql();
        executeStatement(sql);
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    private String generateSql() {
        String colName = columnNameField.getText();
        if (colName == null || colName.trim().isEmpty()) colName = "new_column";
        colName = sanitize(colName.trim());

        String dataType = dataTypeCombo.getValue();
        if (dataType == null || dataType.trim().isEmpty()) dataType = "VARCHAR(255)";

        String tbl = tableName != null ? tableName : "table_name";

        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(tbl)
          .append(" ADD COLUMN ").append(colName)
          .append(" ").append(dataType.trim());

        if (notNullCheck.isSelected()) {
            sb.append(" NOT NULL");
        }

        String defaultVal = defaultValueField.getText();
        if (defaultVal != null && !defaultVal.trim().isEmpty()) {
            sb.append(" DEFAULT ").append(defaultVal.trim());
        }

        sb.append(";");
        return sb.toString();
    }

    private void executeStatement(String sql) {
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
            statusLabel.setText("Column added successfully!");
            statusLabel.setStyle("-fx-text-fill: green;");
            logger.info("Column added to " + tableName + ": " + columnNameField.getText());
            if (onColumnAdded != null) onColumnAdded.run();
            Platform.runLater(() -> {
                try { Thread.sleep(800); } catch (InterruptedException e) { /* ignore */ }
                closeWindow();
            });
        });

        task.setOnFailed(event -> {
            setLoading(false);
            Throwable ex = task.getException();
            showError("Failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            logger.log(Level.SEVERE, "Failed to add column", ex);
        });

        executor.submit(task);
    }

    private void updateSqlPreview() {
        sqlPreviewArea.setText(generateSql());
    }

    private String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        addButton.setDisable(loading);
        columnNameField.setDisable(loading);
        dataTypeCombo.setDisable(loading);
        notNullCheck.setDisable(loading);
        defaultValueField.setDisable(loading);
    }

    private void closeWindow() {
        Stage stage = (Stage) columnNameField.getScene().getWindow();
        stage.close();
        executor.shutdownNow();
    }

    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
        updateSqlPreview();
    }

    public void setOnColumnAdded(Runnable callback) {
        this.onColumnAdded = callback;
    }
}
