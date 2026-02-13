package org.fxsql.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
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
 * Controller for creating new database triggers.
 */
public class CreateTriggerController {

    private static final Logger logger = Logger.getLogger(CreateTriggerController.class.getName());

    @FXML
    private TextField triggerNameField;

    @FXML
    private ComboBox<String> tableNameCombo;

    @FXML
    private ComboBox<String> timingCombo;

    @FXML
    private ComboBox<String> eventCombo;

    @FXML
    private ComboBox<String> forEachCombo;

    @FXML
    private TextField whenConditionField;

    @FXML
    private TextArea triggerBodyArea;

    @FXML
    private TextArea sqlPreviewArea;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private Button createButton;

    private DatabaseConnection databaseConnection;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CreateTrigger-Executor");
        t.setDaemon(true);
        return t;
    });
    private Runnable onTriggerCreated;

    @FXML
    public void initialize() {
        timingCombo.setItems(FXCollections.observableArrayList("BEFORE", "AFTER", "INSTEAD OF"));
        timingCombo.getSelectionModel().select("BEFORE");

        eventCombo.setItems(FXCollections.observableArrayList("INSERT", "UPDATE", "DELETE"));
        eventCombo.getSelectionModel().select("INSERT");

        forEachCombo.setItems(FXCollections.observableArrayList("ROW", "STATEMENT"));
        forEachCombo.getSelectionModel().select("ROW");

        // Live SQL preview listeners
        triggerNameField.textProperty().addListener((obs, o, n) -> updateSqlPreview());
        tableNameCombo.valueProperty().addListener((obs, o, n) -> updateSqlPreview());
        timingCombo.valueProperty().addListener((obs, o, n) -> updateSqlPreview());
        eventCombo.valueProperty().addListener((obs, o, n) -> updateSqlPreview());
        forEachCombo.valueProperty().addListener((obs, o, n) -> updateSqlPreview());
        whenConditionField.textProperty().addListener((obs, o, n) -> updateSqlPreview());
        triggerBodyArea.textProperty().addListener((obs, o, n) -> updateSqlPreview());

        updateSqlPreview();
    }

    @FXML
    private void onCreateTrigger() {
        // Validate trigger name
        String triggerName = triggerNameField.getText();
        if (triggerName == null || triggerName.trim().isEmpty()) {
            showError("Please enter a trigger name.");
            return;
        }

        if (!SQLSanitizer.isValidIdentifier(triggerName.trim())) {
            showError("Invalid trigger name. Use only letters, numbers, and underscores. Must start with a letter or underscore.");
            return;
        }

        // Validate table selection
        if (tableNameCombo.getValue() == null || tableNameCombo.getValue().trim().isEmpty()) {
            showError("Please select a table.");
            return;
        }

        // Validate timing
        if (timingCombo.getValue() == null) {
            showError("Please select a timing (BEFORE, AFTER, or INSTEAD OF).");
            return;
        }

        // Validate event
        if (eventCombo.getValue() == null) {
            showError("Please select an event (INSERT, UPDATE, or DELETE).");
            return;
        }

        // Validate for each
        if (forEachCombo.getValue() == null) {
            showError("Please select FOR EACH ROW or STATEMENT.");
            return;
        }

        // Validate trigger body
        String body = triggerBodyArea.getText();
        if (body == null || body.trim().isEmpty()) {
            showError("Please enter the trigger body SQL statements.");
            return;
        }

        // Check if body is only comments
        String strippedBody = body.trim().replaceAll("(?m)^--.*$", "").trim();
        if (strippedBody.isEmpty()) {
            showError("Trigger body cannot contain only comments. Please enter SQL statements.");
            return;
        }

        String sql = generateCreateTriggerSql();
        if (sql == null || sql.isEmpty()) {
            showError("Failed to generate SQL.");
            return;
        }

        executeCreateTrigger(sql);
    }

    private void executeCreateTrigger(String sql) {
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
            statusLabel.setText("Trigger created successfully!");
            statusLabel.setStyle("-fx-text-fill: green;");
            logger.info("Trigger created: " + triggerNameField.getText());

            if (onTriggerCreated != null) {
                onTriggerCreated.run();
            }

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
            showError("Failed to create trigger: " + errorMsg);
            logger.log(Level.SEVERE, "Failed to create trigger", ex);
        });

        executor.submit(task);
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) triggerNameField.getScene().getWindow();
        stage.close();
        executor.shutdownNow();
    }

    private void updateSqlPreview() {
        String sql = generateCreateTriggerSql();
        sqlPreviewArea.setText(sql);
    }

    private String generateCreateTriggerSql() {
        String triggerName = triggerNameField.getText();
        if (triggerName == null || triggerName.trim().isEmpty()) {
            triggerName = "new_trigger";
        }
        triggerName = sanitizeIdentifier(triggerName.trim());

        String tableName = tableNameCombo.getValue();
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = "table_name";
        }

        String timing = timingCombo.getValue();
        if (timing == null) timing = "BEFORE";

        String event = eventCombo.getValue();
        if (event == null) event = "INSERT";

        String forEach = forEachCombo.getValue();
        if (forEach == null) forEach = "ROW";

        String whenCondition = whenConditionField.getText();
        String body = triggerBodyArea.getText();

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TRIGGER ").append(triggerName).append("\n");
        sb.append(timing).append(" ").append(event).append("\n");
        sb.append("ON ").append(tableName).append("\n");
        sb.append("FOR EACH ").append(forEach).append("\n");

        if (whenCondition != null && !whenCondition.trim().isEmpty()) {
            sb.append("WHEN (").append(whenCondition.trim()).append(")\n");
        }

        sb.append("BEGIN\n");
        if (body != null && !body.trim().isEmpty()) {
            for (String line : body.trim().split("\\R")) {
                sb.append("    ").append(line).append("\n");
            }
        } else {
            sb.append("    -- trigger body\n");
        }
        sb.append("END;");

        return sb.toString();
    }

    private String sanitizeIdentifier(String identifier) {
        return identifier.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        createButton.setDisable(loading);
        triggerNameField.setDisable(loading);
        tableNameCombo.setDisable(loading);
        timingCombo.setDisable(loading);
        eventCombo.setDisable(loading);
        forEachCombo.setDisable(loading);
        whenConditionField.setDisable(loading);
        triggerBodyArea.setDisable(loading);
    }

    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
        loadTableNames();
    }

    private void loadTableNames() {
        if (databaseConnection == null || !databaseConnection.isConnected()) {
            return;
        }

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return databaseConnection.getTableNames();
            }
        };

        task.setOnSucceeded(event -> {
            List<String> tables = task.getValue();
            tableNameCombo.setItems(FXCollections.observableArrayList(tables));
            if (!tables.isEmpty()) {
                tableNameCombo.getSelectionModel().selectFirst();
            }
        });

        task.setOnFailed(event ->
            logger.log(Level.WARNING, "Failed to load table names", task.getException())
        );

        executor.submit(task);
    }

    public void setOnTriggerCreated(Runnable callback) {
        this.onTriggerCreated = callback;
    }
}
