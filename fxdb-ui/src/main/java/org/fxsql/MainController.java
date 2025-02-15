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
import javafx.scene.layout.VBox;
import org.fxsql.components.CircularButton;
import org.fxsql.listeners.DriverEventListener;
import org.fxsql.services.DynamicSQLView;
import org.fxsql.utils.ApplicationTheme;
import org.kordamp.ikonli.feather.Feather;

import java.util.HashSet;
import java.util.Set;

public class MainController {
    private final DriverEventListener driverEventListener = new DriverEventListener();
    public ToggleSwitch themeToggle;
    public TreeView<String> tableBrowser;
    public TableView<ObservableList<Object>> tableView;
    public CircularButton pageDown;
    public CircularButton pageUp;
    public SplitPane mainSplitPane;
    public TabPane actionTabPane;
    public VBox notificationPanel;
    public Tile databaseSelectorTile;
    @Inject
    private DatabaseManager databaseManager;
    private ApplicationTheme currentTheme;
    private DynamicSQLView dynamicSQLView;

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
                connection.connect(connectionString);
                databaseManager.addConnection("sqlite_conn", "sqlite",connectionString, connection);
                System.out.println("Connection added to manager");
            }

            dynamicSQLView.setDatabaseConnection(connection);
            dynamicSQLView.loadTableNames();
        }
    }

    private void loadConnection(String connectionName) {
        // Get the database connection
        System.out.println("Connection Name: " + connectionName);
        ConnectionMetaData metaData = databaseManager.getConnectionMetaData(connectionName);
        DatabaseConnection connection = metaData.getDatabaseConnection();
//        DatabaseConnection connection = databaseManager.getConnection(connectionName);
        if (connection == null) {
            System.out.println("Connection does not exist");
            connection = DatabaseConnectionFactory.getConnection(metaData.getDatabaseType());
            connection.connect(metaData.getDatabaseFilePath());
        }

        if (!connection.isConnected()) {
            System.out.println("Not connected to database");
//            connection.connect(connection.);
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
        actionTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        actionTabPane.setMinWidth(450);
    }


    private void setDatabaseSelectorTile() {
//        databaseSelectorTile = new Tile("Database Selector","Select the data base you need to use");
        databaseSelectorTile.setTitle("Database Selector");
        databaseSelectorTile.setDescription("Select the database you need to use");
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (databaseManager != null) {
                    databaseManager.loadStoredConnections();
                    Set<String> connections = new HashSet<>();
                    connections.add("none");
                    connections.addAll(databaseManager.getConnectionList());

                    ComboBox<String> cmb = new ComboBox<>(FXCollections.observableArrayList(connections));

                    cmb.setOnAction(event -> {
                        String selectedDbConnection = cmb.getValue();
                        if (!"none".equalsIgnoreCase(selectedDbConnection)) {
                            loadConnection(selectedDbConnection);
                        }
                    });

                    Platform.runLater(() -> {
                        cmb.getSelectionModel().selectLast();
                        databaseSelectorTile.setAction(cmb);
                    });
                }
                return null;
            }
        };

        task.run();

    }

    public void initialize() {
        //Initialize toggle switch
        currentTheme = ApplicationTheme.LIGHT;
        themeToggle.selectedProperty().addListener((obs, old, val) -> {
            if (currentTheme == ApplicationTheme.LIGHT) {
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                currentTheme = ApplicationTheme.DARK;
            }
            else {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                currentTheme = ApplicationTheme.LIGHT;
            }
        });

        //Set notification panel to notification listener
        driverEventListener.setNotificationPanel(notificationPanel);

        dynamicSQLView = new DynamicSQLView(tableView, tableBrowser);
        dynamicSQLView.setTabPane(actionTabPane);
        setupTabs();
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.setDividerPositions(0.5);
        setPageDown();
        setPageUp();
        setDatabaseSelectorTile();
    }
}