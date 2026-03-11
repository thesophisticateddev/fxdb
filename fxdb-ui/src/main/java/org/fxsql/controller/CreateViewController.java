package org.fxsql.controller;

import javafx.application.Platform;
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
 * Controller for creating new database views.
 */
public class CreateViewController {

    private static final Logger logger = Logger.getLogger(CreateViewController.class.getName());

    @FXML
    private TextField viewNameField;

    @FXML
    private TextArea viewDefinitionArea;

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
        Thread t = new Thread(r, "CreateView-Executor");
        t.setDaemon(true);
        return t;
    });
    private Runnable onViewCreated;

    @FXML
    public void initialize() {
        viewNameField.textProperty().addListener((obs, o, n) -> updateSqlPreview());
        viewDefinitionArea.textProperty().addListener((obs, o, n) -> updateSqlPreview());
        updateSqlPreview();
    }

    @FXML
    private void onCreateView() {
        String viewName = viewNameField.getText();
        if (viewName == null || viewName.trim().isEmpty()) {
            showError("Please enter a view name.");
            return;
        }

        if (!SQLSanitizer.isValidIdentifier(viewName.trim())) {
            showError("Invalid view name. Use only letters, numbers, and underscores. Must start with a letter or underscore.");
            return;
        }

        String definition = viewDefinitionArea.getText();
        if (definition == null || definition.trim().isEmpty()) {
            showError("Please enter the view definition (SELECT query).");
            return;
        }

        String sql = generateCreateViewSql();
        if (sql == null || sql.isEmpty()) {
            showError("Failed to generate SQL.");
            return;
        }

        executeCreateView(sql);
    }

    private void executeCreateView(String sql) {
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
            statusLabel.setText("View created successfully!");
            statusLabel.setStyle("-fx-text-fill: green;");
            logger.info("View created: " + viewNameField.getText());

            if (onViewCreated != null) {
                onViewCreated.run();
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
            showError("Failed to create view: " + errorMsg);
            logger.log(Level.SEVERE, "Failed to create view", ex);
        });

        executor.submit(task);
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) viewNameField.getScene().getWindow();
        stage.close();
        executor.shutdownNow();
    }

    private void updateSqlPreview() {
        String sql = generateCreateViewSql();
        sqlPreviewArea.setText(sql);
    }

    private String generateCreateViewSql() {
        String viewName = viewNameField.getText();
        if (viewName == null || viewName.trim().isEmpty()) {
            viewName = "new_view";
        }
        viewName = sanitizeIdentifier(viewName.trim());

        String definition = viewDefinitionArea.getText();

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE VIEW ").append(viewName).append(" AS\n");
        if (definition != null && !definition.trim().isEmpty()) {
            sb.append(definition.trim());
        } else {
            sb.append("SELECT ...");
        }
        sb.append(";");

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
        viewNameField.setDisable(loading);
        viewDefinitionArea.setDisable(loading);
    }

    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
    }

    public void setOnViewCreated(Runnable callback) {
        this.onViewCreated = callback;
    }
}
