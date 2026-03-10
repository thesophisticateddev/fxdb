package org.fxsql;

import atlantafx.base.controls.Tile;
import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.dockfx.DockPane;
import org.dockfx.DockPos;
import org.fxsql.components.AboutPane;
import org.fxsql.components.AppMenuBar;
import org.fxsql.components.EditableTablePane;
import org.fxsql.components.alerts.StackTraceAlert;
import org.fxsql.components.notifications.NotificationContainer;
import org.fxsql.components.sqlScriptExecutor.SQLScriptPane;
import org.fxsql.dock.ConnectionDockNode;
import org.fxsql.dock.WorkspaceDockNode;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.driverload.JDBCDriverLoader;
import org.fxsql.listeners.DriverEventListener;
import org.fxsql.listeners.NewConnectionAddedListener;
import org.fxsql.plugins.PluginManager;
import org.fxdb.plugin.sdk.runtime.FXPluginRegistry;
import org.fxdb.plugin.sdk.ui.PluginUIContext;
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

    // FXML-injected fields (from the simplified main.fxml)
    @FXML
    public StackPane dockContainer;
    @FXML
    public NotificationContainer notificationContainer;
    @FXML
    public AppMenuBar appMenuBar;
    @FXML
    public ProgressBar driverLoadProgressBar;
    @FXML
    public Label sqlDialectLabel;
    @FXML
    public HBox progressPanelBox;

    // DockFX components
    private DockPane dockPane;
    private ConnectionDockNode connectionDockNode;
    private WorkspaceDockNode workspaceDockNode;

    // References to components inside dock nodes
    private TreeView<String> tableBrowser;
    private TabPane actionTabPane;
    private Tile databaseSelectorTile;
    private TreeView<String> pluginBrowser;
    private Separator pluginBrowserSeparator;
    private HBox pluginBrowserHeader;

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

        String connectionName = tileComboBox.getSelectionModel().getSelectedItem();
        if (connectionName == null || "none".equalsIgnoreCase(connectionName)) {
            logger.info("No connection selected");
            showNoConnectionAlert();
            return;
        }

        DatabaseConnection connection = databaseManager.getConnection(connectionName);
        if (connection == null) {
            logger.info("Connection not found, attempting to load: " + connectionName);
            loadConnection(connectionName);
            return;
        }

        if (!connection.isConnected()) {
            logger.info("Connection not connected, attempting to reconnect: " + connectionName);
            loadConnection(connectionName);
            return;
        }

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

                var currentConnection = dynamicSQLView.getDatabaseConnection();
                if (currentConnection != null && currentConnection.isConnected()) {
                    System.out.println("Disconnecting current connection");
                    currentConnection.disconnect();
                }

                DatabaseConnection existingConnection = metaData.getDatabaseConnection();
                if (existingConnection != null && existingConnection.isConnected()) {
                    System.out.println("Reusing existing connection for: " + connectionName);
                    return existingConnection;
                }

                System.out.println("Establishing new connection for: " + connectionName);
                return databaseManager.connectByConnectionName(connectionName);
            }
        };

        taskHandle.setOnSucceeded(event -> {
            DatabaseConnection result = taskHandle.getValue();
            if (result != null) {
                if (result.isConnected()) {
                    dynamicSQLView.setDatabaseConnection(result);
                    dynamicSQLView.loadTableNames();
                    updateSqlDialectLabel(connectionName);
                    notificationContainer.showSuccess("Connected to " + connectionName);
                }
                logger.info("Table names loaded for connection: " + connectionName);
            }
        });

        taskHandle.setOnFailed(event -> {
            Throwable e = taskHandle.getException();
            if (e instanceof SQLException) {
                showFailedToConnectAlert((SQLException) e);
            }
            notificationContainer.showError("Failed to connect to " + connectionName);
            logger.severe("Error occurred while updating dynamic sql view " + e.getMessage());
        });

        connectionExecutor.submit(taskHandle);
    }

    private void setDatabaseSelectorTile() {
        databaseSelectorTile.setTitle("Database Selector");
        databaseSelectorTile.setDescription("Select the database you need to use");
        tileComboBox = new ComboBox<>();

        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("Edit Connection");
        MenuItem removeItem = new MenuItem("Remove Connection");

        editItem.setOnAction(e -> onEditConnection());
        removeItem.setOnAction(e -> onRemoveConnection());

        contextMenu.getItems().addAll(editItem, removeItem);
        tileComboBox.setContextMenu(contextMenu);

        tileComboBox.setOnContextMenuRequested(e -> {
            String selected = tileComboBox.getValue();
            boolean isNone = selected == null || "none".equalsIgnoreCase(selected);
            editItem.setDisable(isNone);
            removeItem.setDisable(isNone);
        });

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (databaseManager != null) {
                    databaseManager.loadStoredConnections();
                    Set<String> connections = new HashSet<>();
                    connections.add("none");
                    connections.addAll(databaseManager.getConnectionList());

                    tileComboBox.setItems(FXCollections.observableArrayList(connections));

                    Platform.runLater(() -> {
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
                return;
            }

            if (!"none".equalsIgnoreCase(selectedDbConnection)) {
                if (hasUnsavedChanges()) {
                    handleConnectionSwitch(selectedDbConnection);
                } else {
                    Platform.runLater(() -> switchToConnection(selectedDbConnection));
                }
            } else {
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

    private boolean hasUnsavedChanges() {
        if (actionTabPane != null) {
            for (Tab tab : actionTabPane.getTabs()) {
                if (tab.getContent() instanceof EditableTablePane tablePane) {
                    if (tablePane.hasUnsavedChanges()) {
                        return true;
                    }
                }
                if (tab.getText() != null && tab.getText().endsWith("*")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleConnectionSwitch(String newConnectionName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("You have unsaved changes");

        StringBuilder content = new StringBuilder();
        content.append("Switching to a different connection will affect your current work.\n\n");

        if (actionTabPane != null) {
            for (Tab tab : actionTabPane.getTabs()) {
                if (tab.getContent() instanceof EditableTablePane tablePane) {
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
                saveCurrentWorkspace();
                Platform.runLater(() -> switchToConnection(newConnectionName));
            } else if (result.get() == discardAndSwitch) {
                discardCurrentChanges();
                Platform.runLater(() -> switchToConnection(newConnectionName));
            } else {
                Platform.runLater(() -> tileComboBox.setValue(currentConnectionName));
            }
        } else {
            Platform.runLater(() -> tileComboBox.setValue(currentConnectionName));
        }
    }

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

        if (actionTabPane != null) {
            for (Tab tab : actionTabPane.getTabs()) {
                if (tab.getContent() != null && tab.getText() != null) {
                    WorkspaceState.SqlTabState tabState = new WorkspaceState.SqlTabState(
                            tab.getText(),
                            "",
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

    private void discardCurrentChanges() {
        closeAllTabs();
    }

    private void clearCurrentWorkspace() {
        closeAllTabs();
        if (dynamicSQLView != null) {
            dynamicSQLView.setDatabaseConnection(null);
        }
        sqlDialectLabel.setText("No connection");
    }

    private void closeAllTabs() {
        if (actionTabPane != null) {
            java.util.List<Tab> tabsToClose = new java.util.ArrayList<>(actionTabPane.getTabs());
            for (Tab tab : tabsToClose) {
                if (tab.getContent() instanceof EditableTablePane tablePane) {
                    tablePane.shutdown();
                } else if (tab.getContent() instanceof SQLScriptPane scriptPane) {
                    scriptPane.shutdown();
                } else if (tab.getContent() instanceof org.fxsql.components.TableInfoPane infoPane) {
                    infoPane.shutdown();
                }
            }
            actionTabPane.getTabs().clear();
        }
    }

    private void switchToConnection(String connectionName) {
        closeAllTabs();

        currentConnectionName = connectionName;

        if ("none".equalsIgnoreCase(connectionName)) {
            clearCurrentWorkspace();
            notificationContainer.showInfo("Disconnected");
            return;
        }

        loadConnection(connectionName);

        if (workspaceManager != null) {
            WorkspaceState savedState = workspaceManager.getWorkspace(connectionName);
            if (savedState != null && savedState.getCurrentTableName() != null) {
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
            WindowResult<org.fxsql.controller.NewConnectionController> result =
                    windowManager.loadWindow("new-connection.fxml");

            result.controller.setEditMode(connectionName, metaData);

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

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Remove Connection");
        confirmDialog.setHeaderText("Remove connection: " + connectionName);
        confirmDialog.setContentText("Are you sure you want to remove this connection? This action cannot be undone.");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            databaseManager.removeConnection(connectionName);
            refreshConnectionList();
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
        // Initialize DockFX layout
        initializeDockLayout();

        appMenuBar.setDatabaseManager(databaseManager);
        appMenuBar.setDriverDownloader(driverDownloader);
        appMenuBar.setWindowManager(windowManager);
        jdbcLoader = new JDBCDriverLoader();

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

        driverEventListener.setNotificationContainer(notificationContainer);

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

        // Set up plugin browser tree
        TreeItem<String> pluginBrowserRoot = new TreeItem<>("Plugins");
        pluginBrowserRoot.setExpanded(true);
        pluginBrowser.setRoot(pluginBrowserRoot);
        pluginBrowser.setShowRoot(false);

        // Bridge the SDK's PluginEventBus to the app's EventBus
        org.fxdb.plugin.sdk.event.PluginEventBus.setInstance(event -> org.fxsql.events.EventBus.fireEvent(event));

        // Register shared instances for plugins
        FXPluginRegistry.INSTANCE.addInstance("databaseManager", databaseManager);
        PluginUIContext uiContext = new PluginUIContext(actionTabPane, pluginBrowser,
                pluginBrowserSeparator, pluginBrowserHeader);
        FXPluginRegistry.INSTANCE.addInstance("ui.context", uiContext);

        setDatabaseSelectorTile();

        // Wire refresh button
        connectionDockNode.getRefreshButton().setOnAction(e -> onRefreshData());

        // Set New connection listener
        connectionAddedListener.setDatabaseManager(databaseManager);
        connectionAddedListener.setComboBox(tileComboBox);
        connectionAddedListener.setNotificationContainer(notificationContainer);

        // Set up file open callback for AppMenuBar
        appMenuBar.setOnOpenSqlFile(this::openSqlFileInTab);
        appMenuBar.setOnShowAbout(this::showAboutTab);
    }

    private void initializeDockLayout() {
        // Create the DockPane
        dockPane = new DockPane();

        // Create dock nodes
        connectionDockNode = new ConnectionDockNode();
        workspaceDockNode = new WorkspaceDockNode();

        // Extract component references from dock nodes
        tableBrowser = connectionDockNode.getTableBrowser();
        databaseSelectorTile = connectionDockNode.getDatabaseSelectorTile();
        pluginBrowser = connectionDockNode.getPluginBrowser();
        pluginBrowserSeparator = connectionDockNode.getPluginBrowserSeparator();
        pluginBrowserHeader = connectionDockNode.getPluginBrowserHeader();
        actionTabPane = workspaceDockNode.getTabPane();

        // Dock the nodes
        connectionDockNode.dock(dockPane);
        workspaceDockNode.dock(dockPane, DockPos.RIGHT, connectionDockNode.getDockNode());

        // Add DockPane CSS
        dockPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getStylesheets().add(DockPane.getDefaultUserAgentStyleheet());
            }
        });

        // Add the DockPane to the container
        dockContainer.getChildren().add(dockPane);
    }

    private void openSqlFileInTab(File file) {
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
        pane.setTitleChangeCallback(title -> tab.setText(title));
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

    public void showSuccessNotification(String message) {
        if (notificationContainer != null) {
            notificationContainer.showSuccess(message);
        }
    }

    public void showErrorNotification(String message) {
        if (notificationContainer != null) {
            notificationContainer.showError(message);
        }
    }

    public void showInfoNotification(String message) {
        if (notificationContainer != null) {
            notificationContainer.showInfo(message);
        }
    }

    private void showAboutTab() {
        for (Tab tab : actionTabPane.getTabs()) {
            if (tab.getContent() instanceof AboutPane) {
                actionTabPane.getSelectionModel().select(tab);
                return;
            }
        }

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

        connectionExecutor.shutdownNow();
        if (jdbcLoader != null) {
            jdbcLoader.shutdown();
        }
        if (dynamicSQLView != null) {
            dynamicSQLView.shutdown();
        }
        if (pluginManager != null) {
            pluginManager.shutdown();
        }
        if (actionTabPane != null) {
            for (Tab tab : actionTabPane.getTabs()) {
                if (tab.getContent() instanceof org.fxsql.components.TableInfoPane infoPane) {
                    infoPane.shutdown();
                } else if (tab.getContent() instanceof SQLScriptPane scriptPane) {
                    scriptPane.shutdown();
                } else if (tab.getContent() instanceof EditableTablePane tablePane) {
                    tablePane.shutdown();
                }
            }
        }
        if (databaseManager != null) {
            databaseManager.closeAll();
        }

        try {
            connectionExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Shutdown complete.");
    }
}
