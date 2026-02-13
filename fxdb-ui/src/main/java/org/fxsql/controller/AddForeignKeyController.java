package org.fxsql.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.fxsql.DatabaseConnection;
import org.fxsql.utils.SQLSanitizer;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for adding a foreign key constraint via ALTER TABLE ADD CONSTRAINT FOREIGN KEY.
 */
public class AddForeignKeyController {

    private static final Logger logger = Logger.getLogger(AddForeignKeyController.class.getName());

    private static final ObservableList<String> FK_RULES = FXCollections.observableArrayList(
            "NO ACTION", "CASCADE", "RESTRICT", "SET NULL", "SET DEFAULT"
    );

    @FXML private TextField constraintNameField;
    @FXML private ComboBox<String> fkColumnCombo;
    @FXML private ComboBox<String> refTableCombo;
    @FXML private TextField refColumnField;
    @FXML private ComboBox<String> onUpdateCombo;
    @FXML private ComboBox<String> onDeleteCombo;
    @FXML private TextArea sqlPreviewArea;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Button addButton;

    private DatabaseConnection databaseConnection;
    private String tableName;
    private Runnable onForeignKeyAdded;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AddFK-Executor");
        t.setDaemon(true);
        return t;
    });

    @FXML
    public void initialize() {
        onUpdateCombo.setItems(FK_RULES);
        onUpdateCombo.getSelectionModel().select("NO ACTION");

        onDeleteCombo.setItems(FXCollections.observableArrayList(FK_RULES));
        onDeleteCombo.getSelectionModel().select("NO ACTION");

        constraintNameField.textProperty().addListener((obs, o, n) -> updateSqlPreview());
        fkColumnCombo.valueProperty().addListener((obs, o, n) -> updateSqlPreview());
        refTableCombo.valueProperty().addListener((obs, o, n) -> updateSqlPreview());
        refColumnField.textProperty().addListener((obs, o, n) -> updateSqlPreview());
        onUpdateCombo.valueProperty().addListener((obs, o, n) -> updateSqlPreview());
        onDeleteCombo.valueProperty().addListener((obs, o, n) -> updateSqlPreview());

        updateSqlPreview();
    }

    @FXML
    private void onAddForeignKey() {
        // Validate FK column
        if (fkColumnCombo.getValue() == null || fkColumnCombo.getValue().trim().isEmpty()) {
            showError("Please select the column for the foreign key.");
            return;
        }

        // Validate reference table
        if (refTableCombo.getValue() == null || refTableCombo.getValue().trim().isEmpty()) {
            showError("Please select the referenced table.");
            return;
        }

        // Validate reference column
        String refCol = refColumnField.getText();
        if (refCol == null || refCol.trim().isEmpty()) {
            showError("Please enter the referenced column name.");
            return;
        }
        if (!SQLSanitizer.isValidIdentifier(refCol.trim())) {
            showError("Invalid referenced column name.");
            return;
        }

        // Validate optional constraint name
        String constraintName = constraintNameField.getText();
        if (constraintName != null && !constraintName.trim().isEmpty()
                && !SQLSanitizer.isValidIdentifier(constraintName.trim())) {
            showError("Invalid constraint name. Use only letters, numbers, and underscores.");
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
        String tbl = tableName != null ? tableName : "table_name";
        String fkCol = fkColumnCombo.getValue();
        if (fkCol == null || fkCol.trim().isEmpty()) fkCol = "column_name";

        String refTable = refTableCombo.getValue();
        if (refTable == null || refTable.trim().isEmpty()) refTable = "ref_table";

        String refCol = refColumnField.getText();
        if (refCol == null || refCol.trim().isEmpty()) refCol = "ref_column";
        refCol = sanitize(refCol.trim());

        String onUpdate = onUpdateCombo.getValue();
        if (onUpdate == null) onUpdate = "NO ACTION";

        String onDelete = onDeleteCombo.getValue();
        if (onDelete == null) onDelete = "NO ACTION";

        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(tbl);

        String constraintName = constraintNameField.getText();
        if (constraintName != null && !constraintName.trim().isEmpty()) {
            sb.append(" ADD CONSTRAINT ").append(sanitize(constraintName.trim()));
        } else {
            sb.append(" ADD");
        }

        sb.append(" FOREIGN KEY (").append(fkCol).append(")")
          .append(" REFERENCES ").append(refTable).append("(").append(refCol).append(")")
          .append(" ON UPDATE ").append(onUpdate)
          .append(" ON DELETE ").append(onDelete)
          .append(";");

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
            statusLabel.setText("Foreign key added successfully!");
            statusLabel.setStyle("-fx-text-fill: green;");
            logger.info("Foreign key added to " + tableName);
            if (onForeignKeyAdded != null) onForeignKeyAdded.run();
            Platform.runLater(() -> {
                try { Thread.sleep(800); } catch (InterruptedException e) { /* ignore */ }
                closeWindow();
            });
        });

        task.setOnFailed(event -> {
            setLoading(false);
            Throwable ex = task.getException();
            showError("Failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            logger.log(Level.SEVERE, "Failed to add foreign key", ex);
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
        constraintNameField.setDisable(loading);
        fkColumnCombo.setDisable(loading);
        refTableCombo.setDisable(loading);
        refColumnField.setDisable(loading);
        onUpdateCombo.setDisable(loading);
        onDeleteCombo.setDisable(loading);
    }

    private void closeWindow() {
        Stage stage = (Stage) fkColumnCombo.getScene().getWindow();
        stage.close();
        executor.shutdownNow();
    }

    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
        loadTableNames();
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
        updateSqlPreview();
    }

    public void setColumnNames(List<String> columnNames) {
        fkColumnCombo.setItems(FXCollections.observableArrayList(columnNames));
        if (!columnNames.isEmpty()) {
            fkColumnCombo.getSelectionModel().selectFirst();
        }
    }

    private void loadTableNames() {
        if (databaseConnection == null || !databaseConnection.isConnected()) return;

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return databaseConnection.getTableNames();
            }
        };

        task.setOnSucceeded(event -> {
            List<String> tables = task.getValue();
            refTableCombo.setItems(FXCollections.observableArrayList(tables));
        });

        task.setOnFailed(event ->
            logger.log(Level.WARNING, "Failed to load table names", task.getException())
        );

        executor.submit(task);
    }

    public void setOnForeignKeyAdded(Runnable callback) {
        this.onForeignKeyAdded = callback;
    }
}
