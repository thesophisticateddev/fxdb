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
import org.fxsql.components.AppMenuBar;
import org.fxsql.components.EditableTablePane;
import org.fxsql.components.alerts.StackTraceAlert;
import org.fxsql.components.notifications.NotificationContainer;
import org.fxsql.components.notifications.ToastNotification;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.driverload.JDBCDriverLoader;
import org.fxsql.listeners.DriverEventListener;
import org.fxsql.listeners.NewConnectionAddedListener;
import org.fxsql.service.WindowManager;
import org.fxsql.services.DynamicSQLView;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class MainController {
    private static final Logger logger = Logger.getLogger(MainController.class.getName());
    private final DriverEventListener driverEventListener = new DriverEventListener();
    private final NewConnectionAddedListener connectionAddedListener = new NewConnectionAddedListener();
    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();

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
    public Label driverLoadStatusLabel;
    @FXML
    public HBox progressPanelBox;

    @Inject
    private DatabaseManager databaseManager;

    private DynamicSQLView dynamicSQLView;
    private EditableTablePane editableTablePane;
    private ComboBox<String> tileComboBox;
    private JDBCDriverLoader jdbcLoader;

    @Inject
    private DriverDownloader driverDownloader;
    @Inject
    private WindowManager windowManager;

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
        // Create the editable table pane
        editableTablePane = new EditableTablePane();

        Tab primary = new Tab("Data View");
        primary.setContent(editableTablePane);
        primary.setClosable(false);
        actionTabPane.getTabs().add(primary);
        actionTabPane.getStyleClass().add(Styles.TABS_FLOATING);
        actionTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        actionTabPane.setMinWidth(450);
    }


    private void setDatabaseSelectorTile() {
//        databaseSelectorTile = new Tile("Database Selector","Select the data base you need to use");
        databaseSelectorTile.setTitle("Database Selector");
        databaseSelectorTile.setDescription("Select the database you need to use");
        tileComboBox = new ComboBox<>();
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
                        tileComboBox.getSelectionModel().selectLast();
                        databaseSelectorTile.setAction(tileComboBox);
                    });
                }
                return null;
            }
        };


        tileComboBox.setOnAction(event -> {
            String selectedDbConnection = tileComboBox.getValue();
            if (!"none".equalsIgnoreCase(selectedDbConnection)) {
                Platform.runLater(() -> {
                    loadConnection(selectedDbConnection);
                });
                //loadConnection(selectedDbConnection);
            }
        });
        task.run();

    }

    public void initialize() {
        //Initialize toggle switch
        appMenuBar.setDatabaseManager(databaseManager);
        appMenuBar.setDriverDownloader(driverDownloader);
        appMenuBar.setWindowManager(windowManager);
        jdbcLoader = new JDBCDriverLoader();

        // Load with progress updates
        jdbcLoader.loadAllDriversOnStartupAsync(
                // Completion callback
                result -> {
                    driverLoadProgressBar.setProgress(1.0);

                    if (result.isSuccess()) {
                        driverLoadStatusLabel.setText(String.format("Loaded %d driver(s) successfully", result.successCount()));

                        // Show floating notification instead of alert
                        Platform.runLater(() -> {
                            notificationContainer.showSuccess("Loaded " + result.successCount() + " JDBC driver(s) successfully");
                        });

                    } else {
                        driverLoadStatusLabel.setText("Failed to load drivers");
                        Platform.runLater(() -> {
                            notificationContainer.showError("Failed to load some JDBC drivers");
                        });
                    }
                },
                // Progress callback
                progress -> {
                    double percentage = progress.getPercentage();
                    driverLoadProgressBar.setProgress(percentage / 100.0);
                    driverLoadStatusLabel.setText(String.format("Loading %s... (%d/%d)", progress.currentFile(), progress.current(), progress.total()));
                });

        // Set notification container for event listeners
        driverEventListener.setNotificationContainer(notificationContainer);

        // Set up the tabs with EditableTablePane
        setupTabs();

        // Set up the SQL table view and table browser with EditableTablePane
        dynamicSQLView = new DynamicSQLView(editableTablePane, tableBrowser);
        dynamicSQLView.setTabPane(actionTabPane);

        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.setDividerPositions(0.3);
        setDatabaseSelectorTile();

        // Set New connection listener
        connectionAddedListener.setDatabaseManager(databaseManager);
        connectionAddedListener.setComboBox(tileComboBox);
        connectionAddedListener.setNotificationContainer(notificationContainer);
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

    public void shutdown() {
        // Shutdown the connection executor
        connectionExecutor.shutdown();
        // Shutdown the loader service
        if (jdbcLoader != null) {
            jdbcLoader.shutdown();
        }
        // Shutdown the dynamic SQL view executor
        if (dynamicSQLView != null) {
            dynamicSQLView.shutdown();
        }
        // Close all database connections
        if (databaseManager != null) {
            databaseManager.closeAll();
        }
    }

}