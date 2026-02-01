package org.fxsql.components;

import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import org.fxsql.DatabaseConnection;
import org.fxsql.components.sqlScriptExecutor.SQLScriptPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Context menu for table items in the database browser tree.
 * Provides options to view table data and open SQL scripts.
 */
public class TableContextMenu extends ContextMenu {

    private final TreeView<String> tableSelector;
    private final MenuItem openItem;
    private final MenuItem openScriptWindow;

    private EditableTablePane editableTablePane;
    private DatabaseConnection databaseConnection;
    private TabPane tabPane;

    public TableContextMenu(DatabaseConnection connection, TableView<ObservableList<Object>> tableView,
                            TreeView<String> tableSelector) {
        super();
        this.tableSelector = tableSelector;
        this.databaseConnection = connection;

        // Create menu items with icons
        openItem = new MenuItem("Show Table Data");
        FontIcon openIcon = new FontIcon(Feather.FILE);
        openIcon.setIconSize(14);
        openItem.setGraphic(openIcon);

        openScriptWindow = new MenuItem("Open SQL Script");
        FontIcon scriptIcon = new FontIcon(Feather.FILE_TEXT);
        scriptIcon.setIconSize(14);
        openScriptWindow.setGraphic(scriptIcon);
    }

    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
    }

    public void setTabPane(TabPane tabPane) {
        this.tabPane = tabPane;
    }

    public void setEditableTablePane(EditableTablePane pane) {
        this.editableTablePane = pane;
    }

    private void handleOpenItem() {
        TreeItem<String> selectedItem = tableSelector.getSelectionModel().getSelectedItem();
        if (selectedItem != null && editableTablePane != null && databaseConnection != null) {
            String tableName = selectedItem.getValue();
            editableTablePane.loadTableData(databaseConnection, tableName);
        }
    }

    private void handleOpenScriptWindowInTab() {
        if (tabPane != null && databaseConnection != null) {
            Tab tab = new Tab("SQL Script *");
            SQLScriptPane pane = new SQLScriptPane(databaseConnection);
            tab.setContent(pane);
            tab.setOnClosed(event -> {
                System.out.println("SQL script tab closed");
            });
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
        }
    }

    public void showContextMenu(DatabaseConnection connection, MouseEvent mouseEvent) {
        TreeItem<String> selectedItem = tableSelector.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            this.databaseConnection = connection;
            this.getItems().clear();

            this.getItems().addAll(openItem, openScriptWindow);
            openItem.setOnAction(event -> handleOpenItem());
            openScriptWindow.setOnAction(event -> handleOpenScriptWindowInTab());

            this.show(tableSelector, mouseEvent.getScreenX(), mouseEvent.getScreenY());
        }
    }
}