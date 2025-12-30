package org.fxsql.services;

import atlantafx.base.theme.Tweaks;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import org.fxsql.DatabaseConnection;
import org.fxsql.components.TableContextMenu;

import java.util.List;
import java.util.logging.Logger;

public class DynamicSQLView {

    private static final Logger logger = Logger.getLogger(DynamicSQLView.class.getName());
    private final TableView<ObservableList<Object>> tableView;
    private final TreeView<String> tableSelector;
    private final TableContextMenu tableSelectorContextMenu;
    private final TableInteractionService tableInteractionService;
    private TabPane tabPane;
    private DatabaseConnection databaseConnection;

    public DynamicSQLView(TableView<ObservableList<Object>> tbv, TreeView<String> tableSelector,
                          DatabaseConnection connection) {
        this.tableView = tbv;
        this.tableSelector = tableSelector;
        this.tableSelector.getStyleClass().add(Tweaks.ALT_ICON);
        this.databaseConnection = connection;
        this.tableSelectorContextMenu =
                new TableContextMenu(this.databaseConnection, this.tableView, this.tableSelector);
        this.tableInteractionService = new TableInteractionService(tableView);

    }

    public DynamicSQLView(TableView<ObservableList<Object>> tbv, TreeView<String> tableSelector) {
        this.tableView = tbv;
        this.tableSelector = tableSelector;
        this.tableSelector.getStyleClass().add(Tweaks.ALT_ICON);
        this.tableSelectorContextMenu =
                new TableContextMenu(this.databaseConnection, this.tableView, this.tableSelector);
        this.tableInteractionService = new TableInteractionService(tableView);
    }

    public void loadTableNames() {
        logger.info("Loading table names");
        List<String> tableNames = databaseConnection.getTableNames();
        TreeItem<String> rootItem = new TreeItem<>("Tables");

        if (tableNames != null) {
            List<TreeItem<String>> treeItemList =
                    tableNames.stream().map(tn -> new TreeItem<String>(tn.toLowerCase())).toList();

            rootItem.getChildren().addAll(treeItemList);
        }

        // On double-click load the table data
        tableSelector.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<String> selectedItem = tableSelector.getSelectionModel().getSelectedItem();
                if (selectedItem == rootItem) {
                    logger.info("Root item clicked");
                    return;
                }
                if (selectedItem != null) {
                    logger.info("Double-clicked on: " + selectedItem.getValue());
                    loadTableData(selectedItem.getValue());
                }
            }
            //if right-clicked, then we show a context menu
            if (event.getButton() == MouseButton.SECONDARY) {
                this.tableSelectorContextMenu.showContextMenu(databaseConnection, event);

            }
            //Hide the menu when clicked elsewhere
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                this.tableSelectorContextMenu.hide();
            }
        });


        Platform.runLater(() -> {
            var tableSelectorRoot = tableSelector.getRoot();
            if(tableSelectorRoot != null){
                tableSelectorRoot.getChildren().clear();
            }
            tableSelector.setRoot(rootItem);
            tableSelector.refresh();
        });


    }


    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
        this.tableSelectorContextMenu.setDatabaseConnection(connection);
    }

    public DatabaseConnection getDatabaseConnection(){
        return databaseConnection;
    }

    private void loadTableData(String tableName) {
        this.tableInteractionService.loadTableData(databaseConnection, tableName);
    }

    public void setTabPane(TabPane tabPane) {
        this.tableSelectorContextMenu.setTabPane(tabPane);
        this.tabPane = tabPane;
    }
}
