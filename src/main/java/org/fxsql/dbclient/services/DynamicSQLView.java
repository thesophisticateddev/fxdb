package org.fxsql.dbclient.services;

import atlantafx.base.theme.Tweaks;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.fxsql.dbclient.db.DatabaseConnection;
import tech.tablesaw.api.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DynamicSQLView {

    private final TableView<ObservableList<Object>> tableView;
//    private final ComboBox<String> tableSelector;

    private final TreeView<String> tableSelector;

    private DatabaseConnection databaseConnection;

    public DynamicSQLView(TableView<ObservableList<Object>> tbv, TreeView<String> tableSelector,
                          DatabaseConnection connection) {
        this.tableView = tbv;
        this.tableSelector = tableSelector;
        this.tableSelector.getStyleClass().add(Tweaks.ALT_ICON);
        this.databaseConnection = connection;
    }

    public DynamicSQLView(TableView<ObservableList<Object>> tbv, TreeView<String> tableSelector) {
        this.tableView = tbv;
        this.tableSelector = tableSelector;
        this.tableSelector.getStyleClass().add(Tweaks.ALT_ICON);
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

            tableSelector.getSelectionModel().selectedItemProperty().addListener((obs,oldItem,newItem)->{
                if(newItem != null){
                    System.out.println("Clicked on: " + newItem.getValue()); // Handle item click
                    loadTableData(newItem.getValue());
                }
            });

            Platform.runLater(() -> tableSelector.setRoot(rootItem));

        }).start();
    }


    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
    }

    private void loadTableData(String tableName) {
        new Thread(() -> {
            try (ResultSet rs = databaseConnection.executeReadQuery("SELECT * FROM " + tableName)) {

                Table table = Table.read().db(rs, tableName); // Load into Tablesaw
                Platform.runLater(() -> updateTableView(table));

            }
            catch (SQLException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateTableView(Table table) {
        tableView.getColumns().clear();
        tableView.getItems().clear();

        // Dynamically create columns
        for (int colIndex = 0; colIndex < table.columnCount(); colIndex++) {
            final int index = colIndex;
            TableColumn<ObservableList<Object>, Object> column = new TableColumn<>(table.columnNames().get(colIndex));
            column.setCellValueFactory(
                    data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().get(index)));
            tableView.getColumns().add(column);
        }

        // Populate rows
        ObservableList<ObservableList<Object>> data = FXCollections.observableArrayList();
        for (int row = 0; row < table.rowCount(); row++) {
            ObservableList<Object> rowData = FXCollections.observableArrayList();
            for (int col = 0; col < table.columnCount(); col++) {
                rowData.add(table.column(col).get(row));
            }
            data.add(rowData);
        }

        tableView.setItems(data);
    }
}
