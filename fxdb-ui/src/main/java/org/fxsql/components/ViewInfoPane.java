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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pane that displays view information including its SQL definition and columns.
 */
public class ViewInfoPane extends VBox {

    private static final Logger logger = Logger.getLogger(ViewInfoPane.class.getName());

    private final TabPane tabPane;
    private final Label titleLabel;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;

    private final TextArea definitionArea;
    private final TableView<TableMetaData.ColumnInfo> columnsTable;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ViewInfo-Executor");
        t.setDaemon(true);
        return t;
    });

    private String currentViewName;
    private DatabaseConnection databaseConnection;

    public ViewInfoPane() {
        this.tabPane = new TabPane();
        this.titleLabel = new Label("View Information");
        this.statusLabel = new Label();
        this.progressIndicator = new ProgressIndicator();
        this.definitionArea = new TextArea();
        this.columnsTable = createColumnsTable();

        setupUI();
    }

    private void setupUI() {
        // Title bar
        HBox titleBar = new HBox(10);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(10));

        FontIcon titleIcon = new FontIcon(Feather.EYE);
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

        // Definition tab
        Tab definitionTab = new Tab("Definition");
        definitionArea.setEditable(false);
        definitionArea.setStyle("-fx-font-family: monospace;");
        definitionArea.setWrapText(true);
        definitionTab.setContent(definitionArea);
        definitionTab.setClosable(false);
        FontIcon defIcon = new FontIcon(Feather.FILE_TEXT);
        defIcon.setIconSize(14);
        definitionTab.setGraphic(defIcon);

        // Columns tab
        Tab columnsTab = new Tab("Columns");
        columnsTab.setContent(columnsTable);
        columnsTab.setClosable(false);
        FontIcon colIcon = new FontIcon(Feather.COLUMNS);
        colIcon.setIconSize(14);
        columnsTab.setGraphic(colIcon);

        tabPane.getTabs().addAll(definitionTab, columnsTab);

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

        table.getColumns().addAll(nameCol, typeCol, nullableCol, defaultCol);
        return table;
    }

    /**
     * Loads view information (definition + columns) for the specified view.
     */
    public void loadViewInfo(DatabaseConnection connection, String viewName) {
        this.databaseConnection = connection;
        this.currentViewName = viewName;

        if (connection == null || !connection.isConnected()) {
            showError("No active database connection");
            return;
        }

        setLoading(true);
        titleLabel.setText("View: " + viewName);
        statusLabel.setText("Loading...");
        statusLabel.setStyle("");

        Task<ViewData> loadTask = new Task<>() {
            @Override
            protected ViewData call() throws Exception {
                String definition = connection.getViewDefinition(viewName);
                TableMetaData metadata = connection.getTableMetaData(viewName);
                return new ViewData(definition, metadata);
            }
        };

        loadTask.setOnSucceeded(event -> {
            ViewData data = loadTask.getValue();
            Platform.runLater(() -> {
                definitionArea.setText(data.definition != null && !data.definition.isEmpty()
                        ? data.definition
                        : "-- Unable to retrieve view definition");
                columnsTable.setItems(FXCollections.observableArrayList(data.metadata.getColumns()));
                setLoading(false);
                statusLabel.setText(data.metadata.getColumns().size() + " columns");
            });
        });

        loadTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                setLoading(false);
                Throwable ex = loadTask.getException();
                showError("Failed to load view info: " + (ex != null ? ex.getMessage() : "Unknown error"));
                logger.log(Level.SEVERE, "Failed to load view info", ex);
            });
        });

        executor.submit(loadTask);
    }

    private void refresh() {
        if (databaseConnection != null && currentViewName != null) {
            loadViewInfo(databaseConnection, currentViewName);
        }
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

    public void shutdown() {
        executor.shutdownNow();
    }

    public String getCurrentViewName() {
        return currentViewName;
    }

    private record ViewData(String definition, TableMetaData metadata) {}
}
