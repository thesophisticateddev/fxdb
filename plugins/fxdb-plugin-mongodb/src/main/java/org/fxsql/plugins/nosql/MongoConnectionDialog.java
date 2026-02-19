package org.fxsql.plugins.nosql;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class MongoConnectionDialog extends Dialog<MongoConnectionDialog.ConnectionParams> {

    public record ConnectionParams(
            String host, int port, String database,
            String username, String password, String authDatabase) {}

    private final TextField hostField = new TextField("localhost");
    private final TextField portField = new TextField("27017");
    private final TextField databaseField = new TextField("test");
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField authDatabaseField = new TextField("admin");
    private final Label statusLabel = new Label();

    public MongoConnectionDialog() {
        setTitle("Connect to MongoDB");
        setHeaderText("Enter MongoDB connection details");

        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        ButtonType testButtonType = new ButtonType("Test Connection", ButtonBar.ButtonData.OTHER);
        getDialogPane().getButtonTypes().addAll(connectButtonType, testButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Host:"), 0, 0);
        grid.add(hostField, 1, 0);
        GridPane.setHgrow(hostField, Priority.ALWAYS);

        grid.add(new Label("Port:"), 0, 1);
        grid.add(portField, 1, 1);

        grid.add(new Label("Database:"), 0, 2);
        grid.add(databaseField, 1, 2);

        grid.add(new Separator(), 0, 3, 2, 1);

        Label authLabel = new Label("Authentication (optional)");
        authLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        grid.add(authLabel, 0, 4, 2, 1);

        grid.add(new Label("Username:"), 0, 5);
        grid.add(usernameField, 1, 5);

        grid.add(new Label("Password:"), 0, 6);
        grid.add(passwordField, 1, 6);

        grid.add(new Label("Auth DB:"), 0, 7);
        grid.add(authDatabaseField, 1, 7);

        grid.add(statusLabel, 0, 8, 2, 1);

        getDialogPane().setContent(grid);

        // Handle Test Connection button — prevent dialog from closing
        Button testButton = (Button) getDialogPane().lookupButton(testButtonType);
        testButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            testConnection();
        });

        setResultConverter(dialogButton -> {
            if (dialogButton == connectButtonType) {
                return buildParams();
            }
            return null;
        });
    }

    private ConnectionParams buildParams() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            port = 27017;
        }
        return new ConnectionParams(
                hostField.getText().trim(),
                port,
                databaseField.getText().trim(),
                usernameField.getText().trim(),
                passwordField.getText().trim(),
                authDatabaseField.getText().trim()
        );
    }

    private void testConnection() {
        statusLabel.setText("Testing connection...");
        statusLabel.setStyle("-fx-text-fill: -color-fg-default;");

        ConnectionParams params = buildParams();
        MongoConnectionManager testManager = new MongoConnectionManager();
        try {
            testManager.connect(
                    params.host(), params.port(), params.database(),
                    params.username().isEmpty() ? null : params.username(),
                    params.password().isEmpty() ? null : params.password(),
                    params.authDatabase());
            statusLabel.setText("Connection successful!");
            statusLabel.setStyle("-fx-text-fill: green;");
        } catch (Exception e) {
            statusLabel.setText("Failed: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        } finally {
            testManager.disconnect();
        }
    }
}
