package org.fxsql.components;

import atlantafx.base.theme.Styles;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.fxsql.DatabaseConnection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * A visual indicator showing the health status of a database connection.
 * Supports AtlantaFX theme switching.
 */
public class ConnectionStatusIndicator extends HBox {

    private static final Logger logger = Logger.getLogger(ConnectionStatusIndicator.class.getName());

    public enum Status {
        CONNECTED("success", "Connected"),
        DISCONNECTED("default", "Disconnected"),
        ERROR("danger", "Connection Error"),
        CHECKING("warning", "Checking...");

        private final String styleClass;
        private final String text;

        Status(String styleClass, String text) {
            this.styleClass = styleClass;
            this.text = text;
        }

        public String getStyleClass() {
            return styleClass;
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ConnStatus-Executor");
        t.setDaemon(true);
        return t;
    });

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

        // Use CSS classes instead of inline styles for theme compatibility
        this.getStyleClass().add("connection-status-indicator");

        connectionNameLabel.getStyleClass().addAll("connection-name-label");
        statusLabel.getStyleClass().add("status-label");
        statusDot.getStyleClass().add("status-dot");

        Tooltip.install(this, tooltip);

        this.getChildren().addAll(statusDot, connectionNameLabel, statusLabel);

        // Apply default stylesheet
        applyDefaultStyles();
    }

    /**
     * Applies default CSS styles that work with AtlantaFX themes.
     */
    private void applyDefaultStyles() {
        // Use AtlantaFX color variables that automatically update with theme changes
        String css = """
            .connection-status-indicator {
                -fx-background-color: -color-bg-subtle;
                -fx-background-radius: 4;
            }
            
            .connection-name-label {
                -fx-font-weight: bold;
                -fx-font-size: 12px;
                -fx-text-fill: -color-fg-default;
            }
            
            .status-label {
                -fx-font-size: 11px;
            }
            
            .status-label.success {
                -fx-text-fill: -color-success-emphasis;
            }
            
            .status-label.danger {
                -fx-text-fill: -color-danger-emphasis;
            }
            
            .status-label.warning {
                -fx-text-fill: -color-warning-emphasis;
            }
            
            .status-label.default {
                -fx-text-fill: -color-fg-muted;
            }
            
            .status-dot.success {
                -fx-fill: -color-success-emphasis;
            }
            
            .status-dot.danger {
                -fx-fill: -color-danger-emphasis;
            }
            
            .status-dot.warning {
                -fx-fill: -color-warning-emphasis;
            }
            
            .status-dot.default {
                -fx-fill: -color-fg-muted;
            }
            """;

        this.setStyle(css);
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
            // Remove all previous status style classes
            statusDot.getStyleClass().removeAll("success", "danger", "warning", "default");
            statusLabel.getStyleClass().removeAll("success", "danger", "warning", "default");

            // Add the new status style class
            statusDot.getStyleClass().add(status.getStyleClass());
            statusLabel.getStyleClass().add(status.getStyleClass());

            statusLabel.setText(status.getText());

            // Update tooltip
            String tooltipText = connectionName.isEmpty()
                    ? status.getText()
                    : connectionName + ": " + status.getText();
            tooltip.setText(tooltipText);
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
        executor.shutdownNow();
    }
}