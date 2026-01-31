package org.fxsql;

import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.fxsql.components.AppMenuBar;
import org.fxsql.components.CircularButton;
import org.fxsql.components.alerts.StackTraceAlert;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.driverload.JDBCDriverLoader;
import org.fxsql.listeners.DriverEventListener;
import org.fxsql.listeners.NewConnectionAddedListener;
import org.fxsql.service.WindowManager;
import org.fxsql.services.DynamicSQLView;
import org.kordamp.ikonli.feather.Feather;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class MainController {
    private final DriverEventListener driverEventListener = new DriverEventListener();
    private static Logger logger = Logger.getLogger(MainController.class.getName());
    private final NewConnectionAddedListener connectionAddedListener = new NewConnectionAddedListener();
    public TreeView<String> tableBrowser;
    public TableView<ObservableList<Object>> tableView;
    public CircularButton pageDown;
    public CircularButton pageUp;
    public SplitPane mainSplitPane;
    public TabPane actionTabPane;
    public StackPane notificationPanel;
    public Tile databaseSelectorTile;
    public AppMenuBar appMenuBar;
    public ProgressBar driverLoadProgressBar;
    public Label driverLoadStatusLabel;
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

    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();

    @FXML
    protected void onRefreshData() {
        if (dynamicSQLView != null) {
            //Get current selected connection from tileComboBox
            String connectionName = tileComboBox.getSelectionModel().getSelectedItem();
            if (connectionName == null) {
                return;
            }

            // Get the database connection
            DatabaseConnection connection = databaseManager.getConnection(connectionName);
            if (connection != null && connection.isConnected()) {
                System.out.println("Connection already exists");
            } else if (connectionName.contains("none")) {
                //show alert that no connection has been selected
                logger.info("No connection selected");
            } else {

            }

            dynamicSQLView.setDatabaseConnection(connection);
            dynamicSQLView.loadTableNames();
        }
    }

    private void showFailedToConnectAlert(SQLException exception) {
        StackTraceAlert alert =
                new StackTraceAlert(Alert.AlertType.ERROR, "Error connecting", "Failed to connect to database",
                        "Expand to see stacktrace", exception);
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
                }
                System.out.println("Table names loaded for connection: " + connectionName);
            }
        });

        // 3. Failure Handler (Runs on UI Thread automatically)
        taskHandle.setOnFailed(event -> {
            Throwable e = taskHandle.getException();
            if (e instanceof SQLException) {
                showFailedToConnectAlert((SQLException) e); // Safe to show Alert here
            }
            logger.severe("Error occured while updating dynamic sql view " + e.getMessage());
        });

//        new Thread(taskHandle).start();
        connectionExecutor.submit(taskHandle);
    }

    private void setPageUp() {
        pageUp.setIcon(Feather.CHEVRON_RIGHT);
        pageUp.setOnMouseClicked(event -> {
            System.out.println("Page up Clicked");
        });
    }

    private void setPageDown() {
        pageDown.setIcon(Feather.CHEVRON_LEFT);
        pageDown.setOnMouseClicked(event -> {
            System.out.println("Page down clicked");
        });
    }

    private void setupTabs() {
        Tab primary = new Tab("TableView");
        primary.setContent(tableView);
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
                        driverLoadStatusLabel.setText(
                                String.format("✓ Loaded %d driver(s) successfully", result.successCount())
                        );

                        // Show success alert
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Drivers Loaded");
                        alert.setHeaderText("JDBC Drivers Ready");
                        alert.setContentText(
                                "Successfully loaded " + result.successCount() + " driver(s):\n" +
                                        String.join("\n", result.loadedDrivers())
                        );
                        alert.show();

                    } else {
                        driverLoadStatusLabel.setText("✗ Failed to load drivers");
                        //showErrorDialog(result);
                    }
                },
                // Progress callback
                progress -> {
                    double percentage = progress.getPercentage();
                    driverLoadProgressBar.setProgress(percentage / 100.0);
                    driverLoadStatusLabel.setText(
                            String.format("Loading %s... (%d/%d)",
                                    progress.currentFile(), progress.current(), progress.total())
                    );
                }
        );

        //Set notification panel to notification listener
        driverEventListener.setNotificationPanel(notificationPanel);

        //Set up the SQL table view and table browser
        dynamicSQLView = new DynamicSQLView(tableView, tableBrowser);
        dynamicSQLView.setTabPane(actionTabPane);
        setupTabs();
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.setDividerPositions(0.5);
        setPageDown();
        setPageUp();
        setDatabaseSelectorTile();

        //Set New connection listener
        connectionAddedListener.setDatabaseManager(databaseManager);
        connectionAddedListener.setComboBox(tileComboBox);
    }

    public void shutdown(){
        // Shutdown the connection executor
        connectionExecutor.shutdown();
        // Shutdown the loader service
        jdbcLoader.shutdown();
        // Close all database connections
        if (databaseManager != null) {
            databaseManager.closeAll();
        }
    }

}