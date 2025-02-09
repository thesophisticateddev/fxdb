package org.fxsql.components;

import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import org.fxsql.DatabaseConnection;
import org.fxsql.services.TableInteractionService;


public class TableContextMenu extends ContextMenu {

    private final TableView<ObservableList<Object>> tableView;

    private final TreeView<String> tableSelector;
    private final MenuItem openItem;
    private final MenuItem openScriptWindow;

    private final TableInteractionService tableInteractionService;
    private DatabaseConnection databaseConnection;
    private TabPane tabPane;

    public TableContextMenu(DatabaseConnection connection,TableView<ObservableList<Object>> tableView, TreeView<String> tableSelector) {
        super();
        this.tableView = tableView;
        this.tableSelector = tableSelector;
        openItem = new MenuItem("Show Table");
        openScriptWindow = new MenuItem("Open SQL script");

        this.tableInteractionService = new TableInteractionService(this.tableView);
        this.databaseConnection = connection;

    }

    public void setDatabaseConnection(DatabaseConnection connection){
        this.databaseConnection = connection;
    }

    public void setTabPane(TabPane tabPane){
        this.tabPane = tabPane;
    }
    private void handleOpenItem() {
        TreeItem<String> selectedItem = tableSelector.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            this.tableInteractionService.loadTableData(databaseConnection,selectedItem.getValue());
        }
    }

    private void handleOpenScriptWindowInTab(){
        if(tabPane != null){
            Tab tab = new Tab("Script New *");
            TextArea textArea = new TextArea("# Write SQL here");
            textArea.setEditable(true);
            tab.setContent(textArea);
            tabPane.getTabs().add(tab);
        }
    }

    public void showContextMenu(DatabaseConnection connection, MouseEvent mouseEvent) {
        TreeItem<String> selectedItem = tableSelector.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            this.getItems().clear(); // Clear previous menu items

            this.getItems().addAll(openItem, openScriptWindow);
            openItem.setOnAction(event -> handleOpenItem());
            openScriptWindow.setOnAction(event -> handleOpenScriptWindowInTab());

            this.show(tableSelector, mouseEvent.getScreenX(), mouseEvent.getScreenY());
        }
    }
}
