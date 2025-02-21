package org.fxsql;

import atlantafx.base.controls.Tile;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import com.google.inject.Inject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.fxsql.components.AppMenuBar;
import org.fxsql.components.CircularButton;
import org.fxsql.components.alerts.StackTraceAlert;
import org.fxsql.listeners.DriverEventListener;
import org.fxsql.listeners.NewConnectionAddedListener;
import org.fxsql.services.DynamicSQLView;
import org.fxsql.utils.ApplicationTheme;
import org.kordamp.ikonli.feather.Feather;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class MainController {
    private final DriverEventListener driverEventListener = new DriverEventListener();

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
    @Inject
    private DatabaseManager databaseManager;
    private DynamicSQLView dynamicSQLView;

    private ComboBox<String> tileComboBox;

    @FXML
    protected void onRefreshData() {
        if (dynamicSQLView != null) {
            // Get the database connection
            DatabaseConnection connection = databaseManager.getConnection("sqlite_conn");
            if (connection != null && connection.isConnected()) {
                System.out.println("Connection already exists");
            }
            else {
                connection = DatabaseConnectionFactory.getConnection("sqlite");
                final String connectionString = "jdbc:sqlite:./mydatabase.db";
                try {
                    connection.connect(connectionString);
                }
                catch (SQLException e) {
                    e.printStackTrace();
                    return;
                }
                databaseManager.addConnection("sqlite_conn", "sqlite", connectionString, connection);
                System.out.println("Connection added to manager");
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
        // Get the database connection
        System.out.println("Connection Name: " + connectionName);
        assert databaseManager != null;
        ConnectionMetaData metaData = databaseManager.getConnectionMetaData(connectionName);
        DatabaseConnection connection = null;

        if (metaData != null) {
            connection = metaData.getDatabaseConnection();
        }
//        DatabaseConnection connection = databaseManager.getConnection(connectionName);
        if (connection == null) {
            System.out.println("Connection does not exist");
            assert metaData != null;
            connection = DatabaseConnectionFactory.getConnection(metaData.getDatabaseType());
            try {
                connection.connect(metaData.getDatabaseFilePath());
            }
            catch (SQLException e) {
                // Create Alert
                showFailedToConnectAlert(e);
                return;
            }
        }


        dynamicSQLView.setDatabaseConnection(connection);
        dynamicSQLView.loadTableNames();

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
//                Platform.runLater(() ->{
//                    loadConnection(selectedDbConnection);
//                });
                loadConnection(selectedDbConnection);
            }
        });
        task.run();

    }

    public void initialize() {
        //Initialize toggle switch
        appMenuBar.setDatabaseManager(databaseManager);


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
}