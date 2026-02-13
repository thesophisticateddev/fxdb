package org.fxsql;

import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.fxsql.ConnectionMetaData;
import org.fxsql.components.AboutPane;
import org.fxsql.components.AppMenuBar;
import org.fxsql.components.EditableTablePane;
import org.fxsql.components.alerts.StackTraceAlert;
import org.fxsql.components.notifications.NotificationContainer;
import org.fxsql.components.notifications.ToastNotification;
import org.fxsql.components.sqlScriptExecutor.SQLScriptPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.driverload.JDBCDriverLoader;
import org.fxsql.listeners.DriverEventListener;
import org.fxsql.listeners.NewConnectionAddedListener;
import org.fxsql.plugins.PluginManager;
import org.fxsql.service.WindowManager;
import org.fxsql.service.WindowManager.WindowResult;
import org.fxsql.services.DynamicSQLView;
import org.fxsql.workspace.WorkspaceManager;
import org.fxsql.workspace.WorkspaceState;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class MainController {
    private static final Logger logger = Logger.getLogger(MainController.class.getName());
    private final DriverEventListener driverEventListener = new DriverEventListener();
    private final NewConnectionAddedListener connectionAddedListener = new NewConnectionAddedListener();
    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Connection-Executor");
        t.setDaemon(true);
        return t;
    });

    @FXML
    public TreeView<String> tableBrowser;
    @FXML
    public SplitPane mainSplitPane;
    @FXML
    public TabPane actionTabPane;
    @FXML
    public NotificationContainer notificationContainer;
    @FXML
    public Tile databaseSelectorTile;
    @FXML
    public AppMenuBar appMenuBar;
    @FXML
    public ProgressBar driverLoadProgressBar;
    @FXML
    public Label sqlDialectLabel;
    @FXML
    public HBox progressPanelBox;

    @Inject
    private DatabaseManager databaseManager;

    private DynamicSQLView dynamicSQLView;
    private ComboBox<String> tileComboBox;
    private JDBCDriverLoader jdbcLoader;

    @Inject
    private DriverDownloader driverDownloader;
    @Inject
    private WindowManager windowManager;
    @Inject
    private PluginManager pluginManager;

    private WorkspaceManager workspaceManager;
    private String currentConnectionName = "none";

    @FXML
    protected void onRefreshData() {
        if (dynamicSQLView == null) {
            return;
        }

        // Get current selected connection from tileComboBox
        String connectionName = tileComboBox.getSelectionModel().getSelectedItem();
        if (connectionName == null || "none".equalsIgnoreCase(connectionName)) {
            logger.info("No connection selected");
            showNoConnectionAlert();
            return;
        }

        // Get the database connection
        DatabaseConnection connection = databaseManager.getConnection(connectionName);
        if (connection == null) {
            // Try to load the connection
            logger.info("Connection not found, attempting to load: " + connectionName);
            loadConnection(connectionName);
            return;
        }

        if (!connection.isConnected()) {
            // Try to reconnect
            logger.info("Connection not connected, attempting to reconnect: " + connectionName);
            loadConnection(connectionName);
            return;
        }

        // Refresh the database objects tree
        logger.info("Refreshing database objects for: " + connectionName);
        dynamicSQLView.setDatabaseConnection(connection);
        dynamicSQLView.loadTableNames();
    }

    private void showNoConnectionAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("No Connection");
        alert.setHeaderText("No database connection selected");
        alert.setContentText("Please select a database connection from the dropdown.");
        alert.show();
    }

    private void showFailedToConnectAlert(SQLException exception) {
        StackTraceAlert alert = new StackTraceAlert(Alert.AlertType.ERROR, "Error connecting", "Failed to connect to database", "Expand to see stacktrace", exception);
        alert.showAndWait();
    }

    private void loadConnection(String connectionName) {
        Task<DatabaseConnection> taskHandle = new Task<>() {
            @Override
            protected DatabaseConnection call() throws Exception {
                System.out.println("Loading connection: " + connectionName);
                ConnectionMetaData metaData = databaseManager.getConnectionMetaData(connectionName);

                if (metaData == null) {
                    System.out.println("No metadata found for connection: " + connectionName);
                    return null;
                }

                // Close current connection if exists
                var currentConnection = dynamicSQLView.getDatabaseConnection();
                if (currentConnection != null && currentConnection.isConnected()) {
                    System.out.println("Disconnecting current connection");
                    currentConnection.disconnect();
                }

                // Check if we already have an active connection
                DatabaseConnection existingConnection = metaData.getDatabaseConnection();
                if (existingConnection != null && existingConnection.isConnected()) {
                    System.out.println("Reusing existing connection for: " + connectionName);
                    return existingConnection;
                }

                // Establish new connection
                System.out.println("Establishing new connection for: " + connectionName);
                return databaseManager.connectByConnectionName(connectionName);
            }
        };

        // 2. Success Handler (Runs on UI Thread automatically)
        taskHandle.setOnSucceeded(event -> {
            DatabaseConnection result = taskHandle.getValue();
            if (result != null) {
                if (result.isConnected()) {
                    dynamicSQLView.setDatabaseConnection(result);
                    dynamicSQLView.loadTableNames();

                    // Update SQL dialect label
                    updateSqlDialectLabel(connectionName);

                    // Show success notification
                    notificationContainer.showSuccess("Connected to " + connectionName);
                }
                logger.info("Table names loaded for connection: " + connectionName);
            }
        });

        // 3. Failure Handler (Runs on UI Thread automatically)
        taskHandle.setOnFailed(event -> {
            Throwable e = taskHandle.getException();
            if (e instanceof SQLException) {
                showFailedToConnectAlert((SQLException) e);
            }
            // Show error notification
            notificationContainer.showError("Failed to connect to " + connectionName);
            logger.severe("Error occurred while updating dynamic sql view " + e.getMessage());
        });

//        new Thread(taskHandle).start();
        connectionExecutor.submit(taskHandle);
    }

    private void setupTabs() {
        // Configure tab pane - tables will open in their own tabs
        actionTabPane.getStyleClass().add(Styles.TABS_FLOATING);
        actionTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        actionTabPane.setMinWidth(450);
    }


    private void setDatabaseSelectorTile() {
        databaseSelectorTile.setTitle("Database Selector");
        databaseSelectorTile.setDescription("Select the database you need to use");
        tileComboBox = new ComboBox<>();

        // Create context menu for connection management
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("Edit Connection");
        MenuItem removeItem = new MenuItem("Remove Connection");

        editItem.setOnAction(e -> onEditConnection());
        removeItem.setOnAction(e -> onRemoveConnection());

        contextMenu.getItems().addAll(editItem, removeItem);
        tileComboBox.setContextMenu(contextMenu);

        // Disable context menu items when "none" is selected
        tileComboBox.setOnContextMenuRequested(e -> {
            String selected = tileComboBox.getValue();
            boolean isNone = selected == null || "none".equalsIgnoreCase(selected);
            editItem.setDisable(isNone);
            removeItem.setDisable(isNone);
        });

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (databaseManager != null) {
                    databaseManager.loadStoredConnections();
                    Set<String> connections = new HashSet<>();
                    connections.add("none");
                    connections.addAll(databaseManager.getConnectionList());

                    tileComboBox.setItems(FXCollections.observableArrayList(connections));

                    Platform.runLater(() -> {
                        // Default to "none" - no database selected on startup
                        tileComboBox.getSelectionModel().select("none");
                        databaseSelectorTile.setAction(tileComboBox);
                    });
                }
                return null;
            }
        };

        tileComboBox.setOnAction(event -> {
            String selectedDbConnection = tileComboBox.getValue();
            if (selectedDbConnection == null || selectedDbConnection.equals(currentConnectionName)) {
                return; // No change
            }

            if (!"none".equalsIgnoreCase(selectedDbConnection)) {
                // Check for unsaved changes before switching
                if (hasUnsavedChanges()) {
                    handleConnectionSwitch(selectedDbConnection);
                } else {
                    Platform.runLater(() -> {
                        switchToConnection(selectedDbConnection);
                    });
                }
            } else {
                // Switching to "none"
                if (hasUnsavedChanges()) {
                    handleConnectionSwitch(selectedDbConnection);
                } else {
                    clearCurrentWorkspace();
                    currentConnectionName = "none";
                }
            }
        });
        task.run();
    }

    /**
     * Checks if there are unsaved changes in the current workspace.
     */
    private boolean hasUnsavedChanges() {
        if (actionTabPane != null) {
            for (Tab tab : actionTabPane.getTabs()) {
                // Check EditableTablePane tabs for unsaved changes
                if (tab.getContent() instanceof org.fxsql.components.EditableTablePane tablePane) {
                    if (tablePane.hasUnsavedChanges()) {
                        return true;
                    }
                }
                // Check if tab has unsaved SQL content (tabs with * in title)
                if (tab.getText() != null && tab.getText().endsWith("*")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Handles connection switch with unsaved changes confirmation.
     */
    private void handleConnectionSwitch(String newConnectionName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("You have unsaved changes");

        StringBuilder content = new StringBuilder();
        content.append("Switching to a different connection will affect your current work.\n\n");

        // List all table tabs with unsaved changes
        if (actionTabPane != null) {
            for (Tab tab : actionTabPane.getTabs()) {
                if (tab.getContent() instanceof org.fxsql.components.EditableTablePane tablePane) {
                    if (tablePane.hasUnsavedChanges()) {
                        int changes = tablePane.getUnsavedChangesCount();
                        String tableName = tablePane.getCurrentTableName();
                        content.append("• ").append(changes).append(" unsaved change(s) in table '")
                               .append(tableName != null ? tableName : "unknown").append("'\n");
                    }
                }
            }
        }

        content.append("\nWhat would you like to do?");
        alert.setContentText(content.toString());

        ButtonType saveAndSwitch = new ButtonType("Save & Switch");
        ButtonType discardAndSwitch = new ButtonType("Discard & Switch");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(saveAndSwitch, discardAndSwitch, cancel);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()) {
            if (result.get() == saveAndSwitch) {
                // Save current workspace state before switching
                saveCurrentWorkspace();
                Platform.runLater(() -> switchToConnection(newConnectionName));
            } else if (result.get() == discardAndSwitch) {
                // Discard changes and switch
                discardCurrentChanges();
                Platform.runLater(() -> switchToConnection(newConnectionName));
            } else {
                // Cancel - revert combo box selection
                Platform.runLater(() -> {
                    tileComboBox.setValue(currentConnectionName);
                });
            }
        } else {
            // Dialog was closed without selection - revert
            Platform.runLater(() -> {
                tileComboBox.setValue(currentConnectionName);
            });
        }
    }

    /**
     * Saves the current workspace state to a file.
     */
    private void saveCurrentWorkspace() {
        if (currentConnectionName == null || "none".equalsIgnoreCase(currentConnectionName)) {
            return;
        }

        if (workspaceManager == null) {
            workspaceManager = new WorkspaceManager();
        }

        WorkspaceState state = workspaceManager.getWorkspace(currentConnectionName);
        if (state == null) {
            state = workspaceManager.createWorkspace(currentConnectionName);
        }

        // Save tabs state
        if (actionTabPane != null) {
            for (Tab tab : actionTabPane.getTabs()) {
                if (tab.getContent() != null && tab.getText() != null) {
                    WorkspaceState.SqlTabState tabState = new WorkspaceState.SqlTabState(
                            tab.getText(),
                            "", // Would need to extract SQL content from the tab
                            tab.getText().endsWith("*")
                    );
                    state.getSqlTabs().add(tabState);
                }
            }
        }

        workspaceManager.saveWorkspace(state);
        notificationContainer.showInfo("Workspace saved for " + currentConnectionName);
        logger.info("Workspace saved for connection: " + currentConnectionName);
    }

    /**
     * Discards current changes without saving.
     */
    private void discardCurrentChanges() {
        // Close all table and SQL tabs
        closeAllTabs();
    }

    /**
     * Clears the current workspace without saving.
     */
    private void clearCurrentWorkspace() {
        closeAllTabs();
        if (dynamicSQLView != null) {
            dynamicSQLView.setDatabaseConnection(null);
        }
        sqlDialectLabel.setText("No connection");
    }

    /**
     * Closes all tabs and shuts down their executors.
     */
    private void closeAllTabs() {
        if (actionTabPane != null) {
            // Create a copy to avoid ConcurrentModificationException
            java.util.List<Tab> tabsToClose = new java.util.ArrayList<>(actionTabPane.getTabs());
            for (Tab tab : tabsToClose) {
                if (tab.getContent() instanceof org.fxsql.components.EditableTablePane tablePane) {
                    tablePane.shutdown();
                } else if (tab.getContent() instanceof org.fxsql.components.sqlScriptExecutor.SQLScriptPane scriptPane) {
                    scriptPane.shutdown();
                } else if (tab.getContent() instanceof org.fxsql.components.TableInfoPane infoPane) {
                    infoPane.shutdown();
                }
            }
            actionTabPane.getTabs().clear();
        }
    }

    /**
     * Switches to a new connection.
     */
    private void switchToConnection(String connectionName) {
        // Close all current tabs
        closeAllTabs();

        // Update current connection name
        String previousConnection = currentConnectionName;
        currentConnectionName = connectionName;

        if ("none".equalsIgnoreCase(connectionName)) {
            clearCurrentWorkspace();
            notificationContainer.showInfo("Disconnected");
            return;
        }

        // Load the new connection
        loadConnection(connectionName);

        // Try to restore workspace if available
        if (workspaceManager != null) {
            WorkspaceState savedState = workspaceManager.getWorkspace(connectionName);
            if (savedState != null && savedState.getCurrentTableName() != null) {
                // Notify user that previous workspace is available
                notificationContainer.showInfo("Previous workspace available for " + connectionName);
            }
        }
    }

    private void onEditConnection() {
        String connectionName = tileComboBox.getValue();
        if (connectionName == null || "none".equalsIgnoreCase(connectionName)) {
            return;
        }

        ConnectionMetaData metaData = databaseManager.getConnectionMetaData(connectionName);
        if (metaData == null) {
            notificationContainer.showError("Connection metadata not found: " + connectionName);
            return;
        }

        try {
            // Load the new connection window
            WindowResult<org.fxsql.controller.NewConnectionController> result =
                    windowManager.loadWindow("new-connection.fxml");

            // Configure for edit mode
            result.controller.setEditMode(connectionName, metaData);

            // Create and show the stage
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Edit Connection: " + connectionName);
            stage.setScene(new javafx.scene.Scene(result.root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.show();

        } catch (java.io.IOException e) {
            logger.severe("Failed to open edit connection window: " + e.getMessage());
            notificationContainer.showError("Failed to open edit window");
        }
    }

    private void onRemoveConnection() {
        String connectionName = tileComboBox.getValue();
        if (connectionName == null || "none".equalsIgnoreCase(connectionName)) {
            return;
        }

        // Show confirmation dialog
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Remove Connection");
        confirmDialog.setHeaderText("Remove connection: " + connectionName);
        confirmDialog.setContentText("Are you sure you want to remove this connection? This action cannot be undone.");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Remove the connection
            databaseManager.removeConnection(connectionName);

            // Refresh the combo box
            refreshConnectionList();

            // Show notification
            notificationContainer.showSuccess("Connection removed: " + connectionName);
            logger.info("Connection removed: " + connectionName);
        }
    }

    private void refreshConnectionList() {
        Set<String> connections = new HashSet<>();
        connections.add("none");
        connections.addAll(databaseManager.getConnectionList());
        tileComboBox.setItems(FXCollections.observableArrayList(connections));
        tileComboBox.getSelectionModel().select("none");
    }

    public void initialize() {
        //Initialize toggle switch
        appMenuBar.setDatabaseManager(databaseManager);
        appMenuBar.setDriverDownloader(driverDownloader);
        appMenuBar.setWindowManager(windowManager);
        jdbcLoader = new JDBCDriverLoader();

        // Initialize workspace manager
        workspaceManager = new WorkspaceManager();

        // Load JDBC drivers in background
        jdbcLoader.loadAllDriversOnStartupAsync(
                result -> {
                    Platform.runLater(() -> {
                        driverLoadProgressBar.setVisible(false);
                        driverLoadProgressBar.setManaged(false);
                        if (result.isSuccess()) {
                            notificationContainer.showSuccess("Loaded " + result.successCount() + " JDBC driver(s) successfully");
                        } else {
                            notificationContainer.showError("Failed to load some JDBC drivers");
                        }
                    });
                },
                progress -> {
                    Platform.runLater(() -> {
                        driverLoadProgressBar.setVisible(true);
                        driverLoadProgressBar.setManaged(true);
                        driverLoadProgressBar.setProgress(progress.getPercentage() / 100.0);
                    });
                });

        // Set notification container for event listeners
        driverEventListener.setNotificationContainer(notificationContainer);

        // Set up the tabs
        setupTabs();

        // Add the About tab as the default landing page
        Tab aboutTab = new Tab("About");
        FontIcon aboutIcon = new FontIcon(Feather.INFO);
        aboutIcon.setIconSize(12);
        aboutTab.setGraphic(aboutIcon);
        aboutTab.setContent(new AboutPane());
        actionTabPane.getTabs().add(aboutTab);

        // Set up the SQL table view and table browser
        dynamicSQLView = new DynamicSQLView(null, tableBrowser);
        dynamicSQLView.setTabPane(actionTabPane);

        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.setDividerPositions(0.3);
        setDatabaseSelectorTile();

        // Set New connection listener
        connectionAddedListener.setDatabaseManager(databaseManager);
        connectionAddedListener.setComboBox(tileComboBox);
        connectionAddedListener.setNotificationContainer(notificationContainer);

        // Set up file open callback for AppMenuBar
        appMenuBar.setOnOpenSqlFile(this::openSqlFileInTab);
        appMenuBar.setOnShowAbout(this::showAboutTab);
    }

    /**
     * Opens an SQL file in a new tab.
     */
    private void openSqlFileInTab(File file) {
        // Get current connection (may be null)
        String connectionName = tileComboBox.getValue();
        DatabaseConnection connection = null;
        if (connectionName != null && !"none".equalsIgnoreCase(connectionName)) {
            connection = databaseManager.getConnection(connectionName);
        }

        Tab tab = new Tab(file.getName());
        FontIcon scriptTabIcon = new FontIcon(Feather.FILE_TEXT);
        scriptTabIcon.setIconSize(12);
        tab.setGraphic(scriptTabIcon);

        SQLScriptPane pane = new SQLScriptPane(connection);

        // Set callback to update tab title when file changes
        pane.setTitleChangeCallback(title -> tab.setText(title));

        // Open the file
        pane.openFile(file);

        tab.setContent(pane);
        tab.setOnCloseRequest(event -> {
            if (pane.isModified()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Unsaved Changes");
                alert.setHeaderText("You have unsaved changes");
                alert.setContentText("Do you want to save before closing?");

                ButtonType saveButton = new ButtonType("Save");
                ButtonType discardButton = new ButtonType("Discard");
                ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(saveButton, discardButton, cancelButton);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent()) {
                    if (result.get() == saveButton) {
                        pane.saveFile();
                        if (pane.isModified()) {
                            event.consume();
                            return;
                        }
                    } else if (result.get() == cancelButton) {
                        event.consume();
                        return;
                    }
                } else {
                    event.consume();
                    return;
                }
            }
        });
        tab.setOnClosed(event -> pane.shutdown());

        actionTabPane.getTabs().add(tab);
        actionTabPane.getSelectionModel().select(tab);

        notificationContainer.showInfo("Opened: " + file.getName());
    }

    /**
     * Shows a success notification.
     */
    public void showSuccessNotification(String message) {
        if (notificationContainer != null) {
            notificationContainer.showSuccess(message);
        }
    }

    /**
     * Shows an error notification.
     */
    public void showErrorNotification(String message) {
        if (notificationContainer != null) {
            notificationContainer.showError(message);
        }
    }

    /**
     * Shows an info notification.
     */
    public void showInfoNotification(String message) {
        if (notificationContainer != null) {
            notificationContainer.showInfo(message);
        }
    }

    private void showAboutTab() {
        // Check if an About tab already exists and select it
        for (Tab tab : actionTabPane.getTabs()) {
            if (tab.getContent() instanceof AboutPane) {
                actionTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        // Create a new About tab
        Tab aboutTab = new Tab("About");
        FontIcon aboutIcon = new FontIcon(Feather.INFO);
        aboutIcon.setIconSize(12);
        aboutTab.setGraphic(aboutIcon);
        aboutTab.setContent(new AboutPane());
        actionTabPane.getTabs().add(aboutTab);
        actionTabPane.getSelectionModel().select(aboutTab);
    }

    private void updateSqlDialectLabel(String connectionName) {
        ConnectionMetaData metaData = databaseManager.getConnectionMetaData(connectionName);
        if (metaData != null && metaData.getDatabaseType() != null) {
            String dialect = metaData.getDatabaseType().toUpperCase();
            sqlDialectLabel.setText("SQL Dialect: " + dialect);
        } else {
            sqlDialectLabel.setText("SQL Dialect: Unknown");
        }
    }

    public void shutdown() {
        logger.info("Shutting down application...");

        // Shutdown the connection executor
        connectionExecutor.shutdownNow();
        // Shutdown the loader service
        if (jdbcLoader != null) {
            jdbcLoader.shutdown();
        }
        // Shutdown the dynamic SQL view executor
        if (dynamicSQLView != null) {
            dynamicSQLView.shutdown();
        }
        // Shutdown plugin manager
        if (pluginManager != null) {
            pluginManager.shutdown();
        }
        // Shutdown any open tab panes with executors
        if (actionTabPane != null) {
            for (Tab tab : actionTabPane.getTabs()) {
                if (tab.getContent() instanceof org.fxsql.components.TableInfoPane infoPane) {
                    infoPane.shutdown();
                } else if (tab.getContent() instanceof org.fxsql.components.sqlScriptExecutor.SQLScriptPane scriptPane) {
                    scriptPane.shutdown();
                } else if (tab.getContent() instanceof org.fxsql.components.EditableTablePane tablePane) {
                    tablePane.shutdown();
                }
            }
        }
        // Close all database connections
        if (databaseManager != null) {
            databaseManager.closeAll();
        }

        // Wait briefly for executors to finish, then log completion
        try {
            connectionExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Shutdown complete.");
    }

}