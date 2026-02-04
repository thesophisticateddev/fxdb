package org.fxsql.components;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxsql.DatabaseConnection;
import org.fxsql.model.TableMetaData;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pane that displays table metadata including columns, primary keys, foreign keys, and indexes.
 */
public class TableInfoPane extends VBox {

    private static final Logger logger = Logger.getLogger(TableInfoPane.class.getName());

    private final TabPane tabPane;
    private final Label titleLabel;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;

    private final TableView<TableMetaData.ColumnInfo> columnsTable;
    private final TableView<TableMetaData.PrimaryKeyInfo> primaryKeysTable;
    private final TableView<TableMetaData.ForeignKeyInfo> foreignKeysTable;
    private final TableView<TableMetaData.IndexInfo> indexesTable;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String currentTableName;
    private DatabaseConnection databaseConnection;

    public TableInfoPane() {
        this.tabPane = new TabPane();
        this.titleLabel = new Label("Table Information");
        this.statusLabel = new Label();
        this.progressIndicator = new ProgressIndicator();

        // Create tables
        this.columnsTable = createColumnsTable();
        this.primaryKeysTable = createPrimaryKeysTable();
        this.foreignKeysTable = createForeignKeysTable();
        this.indexesTable = createIndexesTable();

        setupUI();
    }

    private void setupUI() {
        // Title bar - theme aware
        HBox titleBar = new HBox(10);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(10));
        titleBar.getStyleClass().add("table-info-title-bar");

        FontIcon titleIcon = new FontIcon(Feather.INFO);
        titleIcon.setIconSize(18);

        titleLabel.getStyleClass().addAll(Styles.TITLE_4);

        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleBar.getChildren().addAll(titleIcon, titleLabel, spacer, progressIndicator, statusLabel);

        // Create tabs
        Tab columnsTab = new Tab("Columns");
        columnsTab.setContent(columnsTable);
        columnsTab.setClosable(false);
        FontIcon colIcon = new FontIcon(Feather.COLUMNS);
        colIcon.setIconSize(14);
        columnsTab.setGraphic(colIcon);

        Tab pkTab = new Tab("Primary Keys");
        pkTab.setContent(primaryKeysTable);
        pkTab.setClosable(false);
        FontIcon pkIcon = new FontIcon(Feather.KEY);
        pkIcon.setIconSize(14);
        pkTab.setGraphic(pkIcon);

        Tab fkTab = new Tab("Foreign Keys");
        fkTab.setContent(foreignKeysTable);
        fkTab.setClosable(false);
        FontIcon fkIcon = new FontIcon(Feather.LINK);
        fkIcon.setIconSize(14);
        fkTab.setGraphic(fkIcon);

        Tab idxTab = new Tab("Indexes");
        idxTab.setContent(indexesTable);
        idxTab.setClosable(false);
        FontIcon idxIcon = new FontIcon(Feather.LIST);
        idxIcon.setIconSize(14);
        idxTab.setGraphic(idxIcon);

        tabPane.getTabs().addAll(columnsTab, pkTab, fkTab, idxTab);

        // Layout
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        this.getChildren().addAll(titleBar, tabPane);
        this.setSpacing(0);
    }

    private TableView<TableMetaData.ColumnInfo> createColumnsTable() {
        TableView<TableMetaData.ColumnInfo> table = new TableView<>();
        table.setPlaceholder(new Label("No columns to display"));

        TableColumn<TableMetaData.ColumnInfo, String> nameCol = new TableColumn<>("Column Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        nameCol.setPrefWidth(150);

        TableColumn<TableMetaData.ColumnInfo, String> typeCol = new TableColumn<>("Data Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedType()));
        typeCol.setPrefWidth(120);

        TableColumn<TableMetaData.ColumnInfo, String> nullableCol = new TableColumn<>("Nullable");
        nullableCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isNullable() ? "YES" : "NO"));
        nullableCol.setPrefWidth(80);

        TableColumn<TableMetaData.ColumnInfo, String> defaultCol = new TableColumn<>("Default");
        defaultCol.setCellValueFactory(data -> {
            String def = data.getValue().getDefaultValue();
            return new SimpleStringProperty(def != null ? def : "");
        });
        defaultCol.setPrefWidth(100);

        TableColumn<TableMetaData.ColumnInfo, String> autoIncCol = new TableColumn<>("Auto Inc");
        autoIncCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isAutoIncrement() ? "YES" : "NO"));
        autoIncCol.setPrefWidth(80);

        TableColumn<TableMetaData.ColumnInfo, String> remarksCol = new TableColumn<>("Remarks");
        remarksCol.setCellValueFactory(data -> {
            String remarks = data.getValue().getRemarks();
            return new SimpleStringProperty(remarks != null ? remarks : "");
        });
        remarksCol.setPrefWidth(200);

        table.getColumns().addAll(nameCol, typeCol, nullableCol, defaultCol, autoIncCol, remarksCol);
        return table;
    }

    private TableView<TableMetaData.PrimaryKeyInfo> createPrimaryKeysTable() {
        TableView<TableMetaData.PrimaryKeyInfo> table = new TableView<>();
        table.setPlaceholder(new Label("No primary keys defined"));

        TableColumn<TableMetaData.PrimaryKeyInfo, String> nameCol = new TableColumn<>("Key Name");
        nameCol.setCellValueFactory(data -> {
            String name = data.getValue().getPkName();
            return new SimpleStringProperty(name != null ? name : "");
        });
        nameCol.setPrefWidth(200);

        TableColumn<TableMetaData.PrimaryKeyInfo, String> colCol = new TableColumn<>("Column Name");
        colCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getColumnName()));
        colCol.setPrefWidth(200);

        TableColumn<TableMetaData.PrimaryKeyInfo, String> seqCol = new TableColumn<>("Sequence");
        seqCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getKeySeq())));
        seqCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, colCol, seqCol);
        return table;
    }

    private TableView<TableMetaData.ForeignKeyInfo> createForeignKeysTable() {
        TableView<TableMetaData.ForeignKeyInfo> table = new TableView<>();
        table.setPlaceholder(new Label("No foreign keys defined"));

        TableColumn<TableMetaData.ForeignKeyInfo, String> nameCol = new TableColumn<>("FK Name");
        nameCol.setCellValueFactory(data -> {
            String name = data.getValue().getFkName();
            return new SimpleStringProperty(name != null ? name : "");
        });
        nameCol.setPrefWidth(150);

        TableColumn<TableMetaData.ForeignKeyInfo, String> colCol = new TableColumn<>("Column");
        colCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFkColumnName()));
        colCol.setPrefWidth(120);

        TableColumn<TableMetaData.ForeignKeyInfo, String> refTableCol = new TableColumn<>("References Table");
        refTableCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPkTableName()));
        refTableCol.setPrefWidth(150);

        TableColumn<TableMetaData.ForeignKeyInfo, String> refColCol = new TableColumn<>("References Column");
        refColCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPkColumnName()));
        refColCol.setPrefWidth(150);

        TableColumn<TableMetaData.ForeignKeyInfo, String> updateCol = new TableColumn<>("On Update");
        updateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUpdateRuleDescription()));
        updateCol.setPrefWidth(100);

        TableColumn<TableMetaData.ForeignKeyInfo, String> deleteCol = new TableColumn<>("On Delete");
        deleteCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDeleteRuleDescription()));
        deleteCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, colCol, refTableCol, refColCol, updateCol, deleteCol);
        return table;
    }

    private TableView<TableMetaData.IndexInfo> createIndexesTable() {
        TableView<TableMetaData.IndexInfo> table = new TableView<>();
        table.setPlaceholder(new Label("No indexes defined"));

        TableColumn<TableMetaData.IndexInfo, String> nameCol = new TableColumn<>("Index Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getIndexName()));
        nameCol.setPrefWidth(200);

        TableColumn<TableMetaData.IndexInfo, String> colCol = new TableColumn<>("Column");
        colCol.setCellValueFactory(data -> {
            String col = data.getValue().getColumnName();
            return new SimpleStringProperty(col != null ? col : "");
        });
        colCol.setPrefWidth(150);

        TableColumn<TableMetaData.IndexInfo, String> uniqueCol = new TableColumn<>("Unique");
        uniqueCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUniqueDescription()));
        uniqueCol.setPrefWidth(100);

        TableColumn<TableMetaData.IndexInfo, String> orderCol = new TableColumn<>("Order");
        orderCol.setCellValueFactory(data -> {
            String order = data.getValue().getAscOrDesc();
            if (order == null || order.isEmpty()) return new SimpleStringProperty("N/A");
            return new SimpleStringProperty("A".equals(order) ? "Ascending" : "Descending");
        });
        orderCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, colCol, uniqueCol, orderCol);
        return table;
    }

    /**
     * Loads metadata for the specified table.
     */
    public void loadTableInfo(DatabaseConnection connection, String tableName) {
        this.databaseConnection = connection;
        this.currentTableName = tableName;

        if (connection == null || !connection.isConnected()) {
            showError("No active database connection");
            return;
        }

        setLoading(true);
        titleLabel.setText("Table Information: " + tableName);
        statusLabel.setText("Loading...");

        Task<TableMetaData> loadTask = new Task<>() {
            @Override
            protected TableMetaData call() throws Exception {
                return connection.getTableMetaData(tableName);
            }
        };

        loadTask.setOnSucceeded(event -> {
            TableMetaData metadata = loadTask.getValue();
            Platform.runLater(() -> {
                updateTables(metadata);
                setLoading(false);
                statusLabel.setText(String.format("%d columns, %d PK, %d FK, %d indexes",
                        metadata.getColumns().size(),
                        metadata.getPrimaryKeys().size(),
                        metadata.getForeignKeys().size(),
                        metadata.getIndexes().size()));
            });
        });

        loadTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                setLoading(false);
                Throwable ex = loadTask.getException();
                showError("Failed to load metadata: " + (ex != null ? ex.getMessage() : "Unknown error"));
                logger.log(Level.SEVERE, "Failed to load table metadata", ex);
            });
        });

        executor.submit(loadTask);
    }

    private void updateTables(TableMetaData metadata) {
        columnsTable.setItems(FXCollections.observableArrayList(metadata.getColumns()));
        primaryKeysTable.setItems(FXCollections.observableArrayList(metadata.getPrimaryKeys()));
        foreignKeysTable.setItems(FXCollections.observableArrayList(metadata.getForeignKeys()));
        indexesTable.setItems(FXCollections.observableArrayList(metadata.getIndexes()));
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        tabPane.setDisable(loading);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().add(Styles.DANGER);
        logger.warning(message);
    }

    public void clearInfo() {
        columnsTable.getItems().clear();
        primaryKeysTable.getItems().clear();
        foreignKeysTable.getItems().clear();
        indexesTable.getItems().clear();
        titleLabel.setText("Table Information");
        statusLabel.setText("");
        currentTableName = null;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public String getCurrentTableName() {
        return currentTableName;
    }
}
