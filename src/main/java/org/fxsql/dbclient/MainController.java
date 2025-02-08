package org.fxsql.dbclient;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import com.google.inject.Inject;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import org.fxsql.dbclient.components.CircularButton;
import org.fxsql.dbclient.db.DatabaseConnection;
import org.fxsql.dbclient.db.DatabaseConnectionFactory;
import org.fxsql.dbclient.db.DatabaseManager;
import org.fxsql.dbclient.services.DynamicSQLView;
import org.fxsql.dbclient.utils.ApplicationTheme;
import org.kordamp.ikonli.feather.Feather;

public class MainController {
    public ToggleSwitch themeToggle;
    public TreeView<String> tableBrowser;
    public TableView<ObservableList<Object>> tableView;
    public CircularButton pageDown;
    public CircularButton pageUp;
    public SplitPane mainSplitPane;
    public TabPane actionTabPane;

    @Inject
    private DatabaseManager databaseManager;
    private ApplicationTheme currentTheme;

    private DynamicSQLView dynamicSQLView;
    @FXML
    protected void onRefreshData() {
        if(dynamicSQLView != null){
            // Get the database connection
            DatabaseConnection connection = databaseManager.getConnection("sqlite_conn");
            if(connection != null && connection.isConnected()) {
                System.out.println("Connection already exists");
            }
            else{
                connection = DatabaseConnectionFactory.getConnection("sqlite");
                connection.connect("jdbc:sqlite:./mydatabase.db");
                databaseManager.addConnection("sqlite_conn", connection);
                System.out.println("Connection added to manager");
            }

            dynamicSQLView.setDatabaseConnection(connection);
            dynamicSQLView.loadTableNames();
        }
    }

    private void setPageUp(){
        pageUp.setIcon(Feather.CHEVRON_RIGHT);
        pageUp.setOnMouseClicked(event -> {
            System.out.println("Page up Clicked");
        });
    }

    private void setPageDown(){
        pageDown.setIcon(Feather.CHEVRON_LEFT);
        pageDown.setOnMouseClicked(event -> {
            System.out.println("Page down clicked");
        });
    }

    private void setupTabs(){
        Tab primary = new Tab("TableView");
        primary.setContent(tableView);
        actionTabPane.getTabs().add(primary);
        actionTabPane.getStyleClass().add(Styles.TABS_FLOATING);
        actionTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        actionTabPane.setMinWidth(450);
    }

    public void initialize(){
        //Initialize toggle switch
        currentTheme = ApplicationTheme.LIGHT;
        themeToggle.selectedProperty().addListener((obs,old,val)->{
            if(currentTheme == ApplicationTheme.LIGHT){
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                currentTheme = ApplicationTheme.DARK;
            }
            else{
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                currentTheme = ApplicationTheme.LIGHT;
            }
        });



        dynamicSQLView = new DynamicSQLView(tableView,tableBrowser);
        dynamicSQLView.setTabPane(actionTabPane);
        setupTabs();
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.setDividerPositions(0.5);
        setPageDown();
        setPageUp();


    }
}