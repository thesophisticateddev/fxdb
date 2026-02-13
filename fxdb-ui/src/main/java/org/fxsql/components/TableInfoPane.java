package org.fxsql.components;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxsql.DatabaseConnection;
import org.fxsql.controller.AddColumnController;
import org.fxsql.controller.AddForeignKeyController;
import org.fxsql.model.TableMetaData;
import org.fxsql.utils.SQLSanitizer;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A pane that displays table metadata including columns, primary keys, foreign keys, and indexes.
 * Supports editing operations via ALTER TABLE statements.
 */
public class TableInfoPane extends VBox {

    private static final Logger logger = Logger.getLogger(TableInfoPane.class.getName());

    private static final String[] DATA_TYPES = {
            "INTEGER", "BIGINT", "SMALLINT", "DECIMAL(10,2)", "FLOAT", "DOUBLE",
            "BOOLEAN", "VARCHAR(50)", "VARCHAR(100)", "VARCHAR(255)", "TEXT",
            "CHAR(10)", "DATE", "TIME", "TIMESTAMP", "DATETIME", "BLOB", "JSON", "UUID"
    };

    private final TabPane tabPane;
    private final Label titleLabel;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;

    private final TableView<TableMetaData.ColumnInfo> columnsTable;
    private final TableView<TableMetaData.PrimaryKeyInfo> primaryKeysTable;
    private final TableView<TableMetaData.ForeignKeyInfo> foreignKeysTable;
    private final TableView<TableMetaData.IndexInfo> indexesTable;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TableInfo-Executor");
        t.setDaemon(true);
        return t;
    });

    private String currentTableName;
    private DatabaseConnection databaseConnection;

    public TableInfoPane() {
        this.tabPane = new TabPane();
        this.titleLabel = new Label("Table Information");
        this.statusLabel = new Label();
        this.progressIndicator = new ProgressIndicator();

        this.columnsTable = createColumnsTable();
        this.primaryKeysTable = createPrimaryKeysTable();
        this.foreignKeysTable = createForeignKeysTable();
        this.indexesTable = createIndexesTable();

        setupUI();
    }

    private void setupUI() {
        // Title bar
        HBox titleBar = new HBox(10);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(10));
        titleBar.getStyleClass().add("table-info-title-bar");

        FontIcon titleIcon = new FontIcon(Feather.INFO);
        titleIcon.setIconSize(18);

        titleLabel.getStyleClass().addAll(Styles.TITLE_4);

        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        Button refreshButton = new Button("Refresh");
        FontIcon refreshIcon = new FontIcon(Feather.REFRESH_CW);
        refreshIcon.setIconSize(12);
        refreshButton.setGraphic(refreshIcon);
        refreshButton.setStyle("-fx-font-size: 11px;");
        refreshButton.setOnAction(e -> refresh());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleBar.getChildren().addAll(titleIcon, titleLabel, spacer, progressIndicator, statusLabel, refreshButton);

        // Columns tab with toolbar
        Tab columnsTab = new Tab("Columns");
        columnsTab.setContent(createColumnsTabContent());
        columnsTab.setClosable(false);
        FontIcon colIcon = new FontIcon(Feather.COLUMNS);
        colIcon.setIconSize(14);
        columnsTab.setGraphic(colIcon);

        // Primary Keys tab with toolbar
        Tab pkTab = new Tab("Primary Keys");
        pkTab.setContent(createPrimaryKeysTabContent());
        pkTab.setClosable(false);
        FontIcon pkIcon = new FontIcon(Feather.KEY);
        pkIcon.setIconSize(14);
        pkTab.setGraphic(pkIcon);

        // Foreign Keys tab with toolbar
        Tab fkTab = new Tab("Foreign Keys");
        fkTab.setContent(createForeignKeysTabContent());
        fkTab.setClosable(false);
        FontIcon fkIcon = new FontIcon(Feather.LINK);
        fkIcon.setIconSize(14);
        fkTab.setGraphic(fkIcon);

        // Indexes tab (read-only)
        Tab idxTab = new Tab("Indexes");
        idxTab.setContent(indexesTable);
        idxTab.setClosable(false);
        FontIcon idxIcon = new FontIcon(Feather.LIST);
        idxIcon.setIconSize(14);
        idxTab.setGraphic(idxIcon);

        tabPane.getTabs().addAll(columnsTab, pkTab, fkTab, idxTab);

        VBox.setVgrow(tabPane, Priority.ALWAYS);
        this.getChildren().addAll(titleBar, tabPane);
        this.setSpacing(0);
    }

    // ========================== Columns Tab ==========================

    private VBox createColumnsTabContent() {
        VBox content = new VBox();

        Button renameBtn = createToolbarButton("Rename Column", Feather.EDIT);
        renameBtn.setOnAction(e -> onRenameColumn());

        Button changeTypeBtn = createToolbarButton("Change Type", Feather.TYPE);
        changeTypeBtn.setOnAction(e -> onChangeColumnType());

        Button addColBtn = createToolbarButton("Add Column", Feather.PLUS_CIRCLE);
        addColBtn.setStyle("-fx-text-fill: #4CAF50;");
        addColBtn.setOnAction(e -> onAddColumn());

        Button dropColBtn = createToolbarButton("Drop Column", Feather.TRASH_2);
        dropColBtn.setStyle("-fx-text-fill: #f44336;");
        dropColBtn.setOnAction(e -> onDropColumn());

        HBox toolbar = createToolbar(renameBtn, changeTypeBtn, addColBtn, dropColBtn);

        VBox.setVgrow(columnsTable, Priority.ALWAYS);
        content.getChildren().addAll(columnsTable, toolbar);
        return content;
    }

    private void onRenameColumn() {
        TableMetaData.ColumnInfo selected = columnsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a column to rename.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected.getName());
        dialog.setTitle("Rename Column");
        dialog.setHeaderText("Rename column '" + selected.getName() + "'");
        dialog.setContentText("New column name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (newName.trim().isEmpty()) {
                showWarning("Column name cannot be empty.");
                return;
            }
            if (!SQLSanitizer.isValidIdentifier(newName.trim())) {
                showWarning("Invalid column name. Use only letters, numbers, and underscores.");
                return;
            }
            if (newName.trim().equals(selected.getName())) {
                return; // No change
            }

            String sql = "ALTER TABLE " + currentTableName
                    + " RENAME COLUMN " + selected.getName()
                    + " TO " + sanitize(newName.trim()) + ";";
            executeAlterWithConfirmation(sql, "Column renamed successfully.");
        });
    }

    private void onChangeColumnType() {
        TableMetaData.ColumnInfo selected = columnsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a column to change its type.");
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(selected.getFormattedType(),
                DATA_TYPES);
        dialog.setTitle("Change Column Type");
        dialog.setHeaderText("Change type of column '" + selected.getName() + "'");
        dialog.setContentText("New data type:");

        // Make it editable so users can type custom types
        dialog.getDialogPane().lookupAll(".combo-box").forEach(node -> {
            if (node instanceof ComboBox<?> combo) {
                combo.setEditable(true);
            }
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newType -> {
            if (newType.trim().isEmpty()) {
                showWarning("Data type cannot be empty.");
                return;
            }

            // Use ALTER COLUMN ... TYPE which is standard SQL (works on PostgreSQL)
            // MySQL uses MODIFY COLUMN, but ALTER COLUMN TYPE is more portable
            String sql = "ALTER TABLE " + currentTableName
                    + " ALTER COLUMN " + selected.getName()
                    + " TYPE " + newType.trim() + ";";
            executeAlterWithConfirmation(sql, "Column type changed successfully.");
        });
    }

    private void onAddColumn() {
        if (databaseConnection == null || !databaseConnection.isConnected()) {
            showWarning("No active database connection.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("add-column.fxml"));
            Parent root = loader.load();

            AddColumnController controller = loader.getController();
            controller.setDatabaseConnection(databaseConnection);
            controller.setTableName(currentTableName);
            controller.setOnColumnAdded(this::refresh);

            Stage stage = new Stage();
            stage.setTitle("Add Column to " + currentTableName);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to open Add Column dialog", e);
            showError("Could not open Add Column dialog: " + e.getMessage());
        }
    }

    private void onDropColumn() {
        TableMetaData.ColumnInfo selected = columnsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a column to drop.");
            return;
        }

        String sql = "ALTER TABLE " + currentTableName
                + " DROP COLUMN " + selected.getName() + ";";
        executeDestructiveWithConfirmation(sql,
                "Drop Column",
                "Are you sure you want to drop column '" + selected.getName() + "'?\nThis action cannot be undone.",
                "Column dropped successfully.");
    }

    // ========================== Primary Keys Tab ==========================

    private VBox createPrimaryKeysTabContent() {
        VBox content = new VBox();

        Button addPkBtn = createToolbarButton("Add Primary Key", Feather.PLUS_CIRCLE);
        addPkBtn.setStyle("-fx-text-fill: #4CAF50;");
        addPkBtn.setOnAction(e -> onAddPrimaryKey());

        Button dropPkBtn = createToolbarButton("Drop Primary Key", Feather.TRASH_2);
        dropPkBtn.setStyle("-fx-text-fill: #f44336;");
        dropPkBtn.setOnAction(e -> onDropPrimaryKey());

        HBox toolbar = createToolbar(addPkBtn, dropPkBtn);

        VBox.setVgrow(primaryKeysTable, Priority.ALWAYS);
        content.getChildren().addAll(primaryKeysTable, toolbar);
        return content;
    }

    private void onAddPrimaryKey() {
        if (columnsTable.getItems().isEmpty()) {
            showWarning("No columns loaded. Please refresh first.");
            return;
        }

        List<String> columnNames = columnsTable.getItems().stream()
                .map(TableMetaData.ColumnInfo::getName)
                .collect(Collectors.toList());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(columnNames.get(0), columnNames);
        dialog.setTitle("Add Primary Key");
        dialog.setHeaderText("Add a primary key constraint to " + currentTableName);
        dialog.setContentText("Select column:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(colName -> {
            String sql = "ALTER TABLE " + currentTableName
                    + " ADD PRIMARY KEY (" + colName + ");";
            executeAlterWithConfirmation(sql, "Primary key added successfully.");
        });
    }

    private void onDropPrimaryKey() {
        TableMetaData.PrimaryKeyInfo selected = primaryKeysTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a primary key to drop.");
            return;
        }

        String pkName = selected.getPkName();
        String sql;
        if (pkName != null && !pkName.isEmpty()) {
            sql = "ALTER TABLE " + currentTableName + " DROP CONSTRAINT " + pkName + ";";
        } else {
            // Fallback for MySQL which uses DROP PRIMARY KEY without a name
            sql = "ALTER TABLE " + currentTableName + " DROP PRIMARY KEY;";
        }

        executeDestructiveWithConfirmation(sql,
                "Drop Primary Key",
                "Are you sure you want to drop the primary key constraint"
                        + (pkName != null ? " '" + pkName + "'" : "") + "?\nThis action cannot be undone.",
                "Primary key dropped successfully.");
    }

    // ========================== Foreign Keys Tab ==========================

    private VBox createForeignKeysTabContent() {
        VBox content = new VBox();

        Button addFkBtn = createToolbarButton("Add Foreign Key", Feather.PLUS_CIRCLE);
        addFkBtn.setStyle("-fx-text-fill: #4CAF50;");
        addFkBtn.setOnAction(e -> onAddForeignKey());

        Button dropFkBtn = createToolbarButton("Drop Foreign Key", Feather.TRASH_2);
        dropFkBtn.setStyle("-fx-text-fill: #f44336;");
        dropFkBtn.setOnAction(e -> onDropForeignKey());

        HBox toolbar = createToolbar(addFkBtn, dropFkBtn);

        VBox.setVgrow(foreignKeysTable, Priority.ALWAYS);
        content.getChildren().addAll(foreignKeysTable, toolbar);
        return content;
    }

    private void onAddForeignKey() {
        if (databaseConnection == null || !databaseConnection.isConnected()) {
            showWarning("No active database connection.");
            return;
        }

        List<String> columnNames = columnsTable.getItems().stream()
                .map(TableMetaData.ColumnInfo::getName)
                .collect(Collectors.toList());

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("add-foreign-key.fxml"));
            Parent root = loader.load();

            AddForeignKeyController controller = loader.getController();
            controller.setDatabaseConnection(databaseConnection);
            controller.setTableName(currentTableName);
            controller.setColumnNames(columnNames);
            controller.setOnForeignKeyAdded(this::refresh);

            Stage stage = new Stage();
            stage.setTitle("Add Foreign Key to " + currentTableName);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to open Add Foreign Key dialog", e);
            showError("Could not open Add Foreign Key dialog: " + e.getMessage());
        }
    }

    private void onDropForeignKey() {
        TableMetaData.ForeignKeyInfo selected = foreignKeysTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a foreign key to drop.");
            return;
        }

        String fkName = selected.getFkName();
        if (fkName == null || fkName.isEmpty()) {
            showWarning("Cannot determine constraint name for the selected foreign key.");
            return;
        }

        String sql = "ALTER TABLE " + currentTableName + " DROP CONSTRAINT " + fkName + ";";
        executeDestructiveWithConfirmation(sql,
                "Drop Foreign Key",
                "Are you sure you want to drop foreign key '" + fkName + "'?\nThis action cannot be undone.",
                "Foreign key dropped successfully.");
    }

    // ========================== SQL Execution Helpers ==========================

    /**
     * Shows a confirmation dialog with the SQL preview, then executes it asynchronously.
     */
    private void executeAlterWithConfirmation(String sql, String successMsg) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm ALTER TABLE");
        confirm.setHeaderText("The following SQL will be executed:");
        confirm.setContentText(sql);

        ButtonType executeBtn = new ButtonType("Execute");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(executeBtn, cancelBtn);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == executeBtn) {
            executeAlterStatement(sql, successMsg);
        }
    }

    /**
     * Shows a warning confirmation dialog for destructive operations, then executes.
     */
    private void executeDestructiveWithConfirmation(String sql, String title, String warningText, String successMsg) {
        Alert warning = new Alert(Alert.AlertType.WARNING);
        warning.setTitle(title);
        warning.setHeaderText(warningText);
        warning.setContentText("SQL: " + sql);

        ButtonType executeBtn = new ButtonType("Execute");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        warning.getButtonTypes().setAll(executeBtn, cancelBtn);

        Optional<ButtonType> result = warning.showAndWait();
        if (result.isPresent() && result.get() == executeBtn) {
            executeAlterStatement(sql, successMsg);
        }
    }

    /**
     * Executes an ALTER TABLE statement asynchronously and refreshes metadata on success.
     */
    private void executeAlterStatement(String sql, String successMsg) {
        if (databaseConnection == null || !databaseConnection.isConnected()) {
            showError("No active database connection.");
            return;
        }

        setLoading(true);
        statusLabel.setText("Executing...");
        statusLabel.setStyle("");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                databaseConnection.executeWriteQuery(sql);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            logger.info(successMsg + " SQL: " + sql);
            // Refresh metadata to reflect changes
            refresh();
        });

        task.setOnFailed(event -> {
            setLoading(false);
            Throwable ex = task.getException();
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            showError("Failed: " + errorMsg);
            logger.log(Level.SEVERE, "ALTER TABLE failed: " + sql, ex);

            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("ALTER TABLE Failed");
            errorAlert.setHeaderText("The statement could not be executed.");
            errorAlert.setContentText(errorMsg);
            errorAlert.show();
        });

        executor.submit(task);
    }

    // ========================== UI Helpers ==========================

    private Button createToolbarButton(String text, Feather icon) {
        Button btn = new Button(text);
        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(12);
        btn.setGraphic(fi);
        btn.setStyle("-fx-font-size: 11px;");
        return btn;
    }

    private HBox createToolbar(Button... buttons) {
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(6, 8, 6, 8));
        toolbar.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");
        toolbar.getChildren().addAll(buttons);
        return toolbar;
    }

    private String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Action Required");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    // ========================== Table Creation (same as before) ==========================

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

    // ========================== Data Loading ==========================

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
        statusLabel.setStyle("");

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

    /**
     * Refreshes the table info by reloading metadata.
     */
    private void refresh() {
        if (databaseConnection != null && currentTableName != null) {
            loadTableInfo(databaseConnection, currentTableName);
        }
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
