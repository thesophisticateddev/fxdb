package org.fxsql.dbclient.services;

import atlantafx.base.theme.Tweaks;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import org.fxsql.dbclient.components.TableContextMenu;
import org.fxsql.dbclient.db.DatabaseConnection;

import java.util.List;

public class DynamicSQLView {

    private final TableView<ObservableList<Object>> tableView;

    private final TreeView<String> tableSelector;
    private final TableContextMenu tableSelectorContextMenu;
    private final TableInteractionService tableInteractionService;
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
        new Thread(() -> {

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
                    if (selectedItem != null) {
                        System.out.println("Double-clicked on: " + selectedItem.getValue());
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


            Platform.runLater(() -> tableSelector.setRoot(rootItem));

        }).start();
    }


    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
        this.tableSelectorContextMenu.setDatabaseConnection(connection);
    }

    private void loadTableData(String tableName) {
        this.tableInteractionService.loadTableData(databaseConnection, tableName);
    }

    public void setTabPane(TabPane tabPane){
        this.tableSelectorContextMenu.setTabPane(tabPane);
    }
}
