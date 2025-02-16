package org.fxsql.services;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.fxsql.DatabaseConnection;
import tech.tablesaw.api.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TableInteractionService {

    private static Logger logger = Logger.getLogger(TableInteractionService.class.getName());
    private final TableView<ObservableList<Object>> tableView;

    public TableInteractionService( TableView<ObservableList<Object>> tv){
        this.tableView =tv;
    }


    public void loadTableData(DatabaseConnection databaseConnection, String tableName) {
        new Thread(() -> {
            try (ResultSet rs = databaseConnection.executeReadQuery("SELECT * FROM " + tableName + " LIMIT 200");) {
                if(rs == null){
                    logger.warning("No data found in table!");
                    return;
                }
//                rs.setFetchSize(200);
                Table table = Table.read().db(rs, tableName); // Load into Tablesaw
                Platform.runLater(() -> updateTableView(table));
            }
            catch (SQLException e) {
//                e.printStackTrace();
                logger.log(Level.SEVERE,"Failed to get data from table",e);
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
