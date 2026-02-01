package org.fxsql.components;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.fxsql.DatabaseConnection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * A visual indicator showing the health status of a database connection.
 */
public class ConnectionStatusIndicator extends HBox {

    private static final Logger logger = Logger.getLogger(ConnectionStatusIndicator.class.getName());

    public enum Status {
        CONNECTED("#4CAF50", "Connected"),      // Green
        DISCONNECTED("#9E9E9E", "Disconnected"), // Gray
        ERROR("#F44336", "Connection Error"),    // Red
        CHECKING("#FFC107", "Checking...");     // Yellow/Amber

        private final String color;
        private final String text;

        Status(String color, String text) {
            this.color = color;
            this.text = text;
        }

        public String getColor() {
            return color;
        }

        public String getText() {
            return text;
        }
    }

    private final Circle statusDot;
    private final Label statusLabel;
    private final Label connectionNameLabel;
    private final Tooltip tooltip;

    private DatabaseConnection databaseConnection;
    private String connectionName = "";
    private Status currentStatus = Status.DISCONNECTED;
    private Timeline healthCheckTimeline;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ConnectionStatusIndicator() {
        this.statusDot = new Circle(6);
        this.statusLabel = new Label();
        this.connectionNameLabel = new Label();
        this.tooltip = new Tooltip();

        setupUI();
        setStatus(Status.DISCONNECTED);
    }

    private void setupUI() {
        this.setAlignment(Pos.CENTER_LEFT);
        this.setSpacing(8);
        this.setPadding(new Insets(4, 8, 4, 8));
        this.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 4;");

        connectionNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        statusLabel.setStyle("-fx-font-size: 11px;");

        Tooltip.install(this, tooltip);

        this.getChildren().addAll(statusDot, connectionNameLabel, statusLabel);
    }

    /**
     * Sets the database connection to monitor.
     */
    public void setConnection(DatabaseConnection connection, String name) {
        this.databaseConnection = connection;
        this.connectionName = name != null ? name : "";

        Platform.runLater(() -> {
            connectionNameLabel.setText(connectionName);
        });

        // Start health check
        checkConnectionHealth();
        startPeriodicHealthCheck();
    }

    /**
     * Clears the connection.
     */
    public void clearConnection() {
        stopHealthCheck();
        this.databaseConnection = null;
        this.connectionName = "";

        Platform.runLater(() -> {
            connectionNameLabel.setText("");
            setStatus(Status.DISCONNECTED);
        });
    }

    /**
     * Manually triggers a health check.
     */
    public void checkConnectionHealth() {
        if (databaseConnection == null) {
            setStatus(Status.DISCONNECTED);
            return;
        }

        setStatus(Status.CHECKING);

        executor.submit(() -> {
            try {
                boolean isConnected = databaseConnection.isConnected();

                Platform.runLater(() -> {
                    if (isConnected) {
                        setStatus(Status.CONNECTED);
                    } else {
                        setStatus(Status.DISCONNECTED);
                    }
                });
            } catch (Exception e) {
                logger.warning("Connection health check failed: " + e.getMessage());
                Platform.runLater(() -> setStatus(Status.ERROR));
            }
        });
    }

    /**
     * Starts periodic health checks (every 30 seconds).
     */
    private void startPeriodicHealthCheck() {
        stopHealthCheck();

        healthCheckTimeline = new Timeline(
                new KeyFrame(Duration.seconds(30), event -> checkConnectionHealth())
        );
        healthCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        healthCheckTimeline.play();
    }

    /**
     * Stops periodic health checks.
     */
    public void stopHealthCheck() {
        if (healthCheckTimeline != null) {
            healthCheckTimeline.stop();
            healthCheckTimeline = null;
        }
    }

    /**
     * Sets the status and updates the UI.
     */
    public void setStatus(Status status) {
        this.currentStatus = status;

        Platform.runLater(() -> {
            statusDot.setFill(Color.web(status.getColor()));
            statusLabel.setText(status.getText());

            // Update tooltip
            String tooltipText = connectionName.isEmpty()
                    ? status.getText()
                    : connectionName + ": " + status.getText();
            tooltip.setText(tooltipText);

            // Update label color based on status
            switch (status) {
                case CONNECTED -> statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #4CAF50;");
                case ERROR -> statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #F44336;");
                case CHECKING -> statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #FFC107;");
                default -> statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #9E9E9E;");
            }
        });
    }

    /**
     * Returns the current status.
     */
    public Status getStatus() {
        return currentStatus;
    }

    /**
     * Returns whether the connection is healthy.
     */
    public boolean isHealthy() {
        return currentStatus == Status.CONNECTED;
    }

    /**
     * Shuts down the executor.
     */
    public void shutdown() {
        stopHealthCheck();
        executor.shutdown();
    }
}