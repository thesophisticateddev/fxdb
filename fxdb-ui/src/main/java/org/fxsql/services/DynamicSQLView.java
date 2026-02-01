package org.fxsql.services;

import atlantafx.base.theme.Tweaks;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxsql.DatabaseConnection;
import org.fxsql.DatabaseObjects;
import org.fxsql.components.EditableTablePane;
import org.fxsql.components.TableContextMenu;
import org.fxsql.controller.CreateTableController;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dynamic view for displaying database objects in a tree structure.
 * Supports Tables, Views, Triggers, Functions, and Indexes.
 */
public class DynamicSQLView {

    private static final Logger logger = Logger.getLogger(DynamicSQLView.class.getName());

    private final TreeView<String> tableSelector;
    private final TableContextMenu tableSelectorContextMenu;
    private final ContextMenu categoryContextMenu;
    private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor();

    private TabPane tabPane;
    private EditableTablePane editableTablePane;
    private DatabaseConnection databaseConnection;
    private volatile boolean isRefreshing = false;

    // Tree root items for each category
    private TreeItem<String> rootItem;
    private TreeItem<String> tablesNode;
    private TreeItem<String> viewsNode;
    private TreeItem<String> triggersNode;
    private TreeItem<String> functionsNode;
    private TreeItem<String> indexesNode;

    public DynamicSQLView(EditableTablePane editableTablePane, TreeView<String> tableSelector,
                          DatabaseConnection connection) {
        this.editableTablePane = editableTablePane;
        this.tableSelector = tableSelector;
        this.tableSelector.getStyleClass().add(Tweaks.ALT_ICON);
        this.databaseConnection = connection;
        this.tableSelectorContextMenu = new TableContextMenu(this.databaseConnection,
                this.editableTablePane != null ? this.editableTablePane.getTableView() : null,
                this.tableSelector);
        this.tableSelectorContextMenu.setEditableTablePane(editableTablePane);
        this.categoryContextMenu = createCategoryContextMenu();

        initializeTreeStructure();
        setupEventHandlers();
    }

    public DynamicSQLView(EditableTablePane editableTablePane, TreeView<String> tableSelector) {
        this.editableTablePane = editableTablePane;
        this.tableSelector = tableSelector;
        this.tableSelector.getStyleClass().add(Tweaks.ALT_ICON);
        this.tableSelectorContextMenu = new TableContextMenu(this.databaseConnection,
                this.editableTablePane != null ? this.editableTablePane.getTableView() : null,
                this.tableSelector);
        this.tableSelectorContextMenu.setEditableTablePane(editableTablePane);
        this.categoryContextMenu = createCategoryContextMenu();

        initializeTreeStructure();
        setupEventHandlers();
    }

    /**
     * Creates the context menu for category nodes.
     */
    private ContextMenu createCategoryContextMenu() {
        ContextMenu menu = new ContextMenu();
        return menu;
    }

    /**
     * Initializes the tree structure with category nodes.
     */
    private void initializeTreeStructure() {
        rootItem = new TreeItem<>("Database");
        rootItem.setExpanded(true);

        tablesNode = createCategoryNode("Tables", Feather.DATABASE);
        viewsNode = createCategoryNode("Views", Feather.EYE);
        triggersNode = createCategoryNode("Triggers", Feather.ZAP);
        functionsNode = createCategoryNode("Functions", Feather.CODE);
        indexesNode = createCategoryNode("Indexes", Feather.LIST);

        rootItem.getChildren().addAll(tablesNode, viewsNode, triggersNode, functionsNode, indexesNode);

        Platform.runLater(() -> tableSelector.setRoot(rootItem));
    }

    /**
     * Creates a category node with an icon.
     */
    private TreeItem<String> createCategoryNode(String name, Feather icon) {
        TreeItem<String> node = new TreeItem<>(name);
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(14);
        node.setGraphic(fontIcon);
        node.setExpanded(true);
        return node;
    }

    /**
     * Sets up mouse event handlers for the tree view.
     */
    private void setupEventHandlers() {
        tableSelector.setOnMouseClicked(event -> {
            TreeItem<String> selectedItem = tableSelector.getSelectionModel().getSelectedItem();

            if (selectedItem == null) {
                return;
            }

            // Double-click to load table/view data
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                handleDoubleClick(selectedItem);
            }

            // Right-click for context menu
            if (event.getButton() == MouseButton.SECONDARY) {
                if (isCategoryNode(selectedItem)) {
                    // Show category-specific context menu
                    showCategoryContextMenu(selectedItem, event);
                    return;
                }
                this.tableSelectorContextMenu.showContextMenu(databaseConnection, event);
            }

            // Left click to hide context menus
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                this.tableSelectorContextMenu.hide();
                this.categoryContextMenu.hide();
            }
        });
    }

    /**
     * Shows the context menu for category nodes.
     */
    private void showCategoryContextMenu(TreeItem<String> categoryItem, MouseEvent event) {
        categoryContextMenu.getItems().clear();

        // Add menu items based on category type
        if (isTablesCategory(categoryItem)) {
            MenuItem createTableItem = new MenuItem("Create New Table...");
            FontIcon icon = new FontIcon(Feather.PLUS_CIRCLE);
            icon.setIconSize(14);
            createTableItem.setGraphic(icon);
            createTableItem.setOnAction(e -> openCreateTableDialog());
            categoryContextMenu.getItems().add(createTableItem);
        }

        // Add refresh option for all categories
        MenuItem refreshItem = new MenuItem("Refresh");
        FontIcon refreshIcon = new FontIcon(Feather.REFRESH_CW);
        refreshIcon.setIconSize(14);
        refreshItem.setGraphic(refreshIcon);
        refreshItem.setOnAction(e -> loadTableNames());

        if (!categoryContextMenu.getItems().isEmpty()) {
            categoryContextMenu.getItems().add(new SeparatorMenuItem());
        }
        categoryContextMenu.getItems().add(refreshItem);

        categoryContextMenu.show(tableSelector, event.getScreenX(), event.getScreenY());
    }

    /**
     * Checks if the item is the Tables category node.
     */
    private boolean isTablesCategory(TreeItem<String> item) {
        return item == tablesNode || (item.getValue() != null && item.getValue().startsWith("Tables"));
    }

    /**
     * Opens the Create Table dialog.
     */
    private void openCreateTableDialog() {
        if (databaseConnection == null || !databaseConnection.isConnected()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Connection");
            alert.setHeaderText("No active database connection");
            alert.setContentText("Please connect to a database first.");
            alert.show();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("create-table.fxml"));
            Parent root = loader.load();

            CreateTableController controller = loader.getController();
            controller.setDatabaseConnection(databaseConnection);
            controller.setOnTableCreated(this::loadTableNames);

            Stage stage = new Stage();
            stage.setTitle("Create New Table");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to open Create Table dialog", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to open dialog");
            alert.setContentText("Could not open the Create Table dialog: " + e.getMessage());
            alert.show();
        }
    }

    /**
     * Handles double-click on a tree item.
     */
    private void handleDoubleClick(TreeItem<String> selectedItem) {
        // Don't load data for category nodes or root
        if (isCategoryNode(selectedItem) || selectedItem == rootItem) {
            logger.fine("Category or root item clicked, ignoring");
            return;
        }

        String itemName = selectedItem.getValue();
        TreeItem<String> parent = selectedItem.getParent();

        if (parent == null) {
            return;
        }

        String parentCategory = parent.getValue();
        logger.info("Double-clicked on: " + itemName + " (Category: " + parentCategory + ")");

        // Only load data for Tables and Views (handle labels like "Tables (5)")
        if (parentCategory != null &&
            (parentCategory.startsWith("Tables") || parentCategory.startsWith("Views"))) {
            loadTableData(itemName);
        }
    }

    /**
     * Checks if the given item is a category node.
     */
    private boolean isCategoryNode(TreeItem<String> item) {
        if (item == rootItem) {
            return true;
        }
        // Check by reference first
        if (item == tablesNode || item == viewsNode ||
            item == triggersNode || item == functionsNode ||
            item == indexesNode) {
            return true;
        }
        // Also check by parent (category nodes have rootItem as parent)
        return item.getParent() == rootItem;
    }

    /**
     * Loads all database objects asynchronously.
     * This is the main refresh method called externally.
     */
    public void loadTableNames() {
        if (isRefreshing) {
            logger.info("Refresh already in progress, skipping");
            return;
        }

        if (databaseConnection == null || !databaseConnection.isConnected()) {
            logger.warning("Cannot refresh: No active database connection");
            clearAllNodes();
            return;
        }

        isRefreshing = true;
        logger.info("Starting database objects refresh...");

        Task<DatabaseObjects> loadTask = new Task<>() {
            @Override
            protected DatabaseObjects call() throws Exception {
                return databaseConnection.getAllDatabaseObjects();
            }
        };

        loadTask.setOnSucceeded(event -> {
            DatabaseObjects objects = loadTask.getValue();
            updateTreeView(objects);
            isRefreshing = false;
            logger.info("Database objects refresh completed successfully");
        });

        loadTask.setOnFailed(event -> {
            Throwable error = loadTask.getException();
            logger.log(Level.SEVERE, "Failed to load database objects", error);
            isRefreshing = false;

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Refresh Failed");
                alert.setHeaderText("Failed to load database objects");
                alert.setContentText(error != null ? error.getMessage() : "Unknown error");
                alert.show();
            });
        });

        refreshExecutor.submit(loadTask);
    }

    /**
     * Updates the tree view with the loaded database objects.
     */
    private void updateTreeView(DatabaseObjects objects) {
        Platform.runLater(() -> {
            // Clear existing children
            tablesNode.getChildren().clear();
            viewsNode.getChildren().clear();
            triggersNode.getChildren().clear();
            functionsNode.getChildren().clear();
            indexesNode.getChildren().clear();

            // Populate tables
            populateNode(tablesNode, objects.getTables(), Feather.GRID);

            // Populate views
            populateNode(viewsNode, objects.getViews(), Feather.EYE);

            // Populate triggers
            populateNode(triggersNode, objects.getTriggers(), Feather.ZAP);

            // Populate functions
            populateNode(functionsNode, objects.getFunctions(), Feather.CODE);

            // Populate indexes
            populateNode(indexesNode, objects.getIndexes(), Feather.LIST);

            // Update category labels with counts
            updateCategoryLabel(tablesNode, "Tables", objects.getTables().size());
            updateCategoryLabel(viewsNode, "Views", objects.getViews().size());
            updateCategoryLabel(triggersNode, "Triggers", objects.getTriggers().size());
            updateCategoryLabel(functionsNode, "Functions", objects.getFunctions().size());
            updateCategoryLabel(indexesNode, "Indexes", objects.getIndexes().size());

            // Refresh the tree view
            tableSelector.refresh();
        });
    }

    /**
     * Populates a tree node with items.
     */
    private void populateNode(TreeItem<String> parentNode, List<String> items, Feather icon) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (String item : items) {
            TreeItem<String> childItem = new TreeItem<>(item.toLowerCase());
            FontIcon fontIcon = new FontIcon(icon);
            fontIcon.setIconSize(12);
            childItem.setGraphic(fontIcon);
            parentNode.getChildren().add(childItem);
        }
    }

    /**
     * Updates the category label to show count.
     */
    private void updateCategoryLabel(TreeItem<String> node, String baseName, int count) {
        node.setValue(baseName + " (" + count + ")");
    }

    /**
     * Clears all nodes in the tree.
     */
    private void clearAllNodes() {
        Platform.runLater(() -> {
            tablesNode.getChildren().clear();
            viewsNode.getChildren().clear();
            triggersNode.getChildren().clear();
            functionsNode.getChildren().clear();
            indexesNode.getChildren().clear();

            updateCategoryLabel(tablesNode, "Tables", 0);
            updateCategoryLabel(viewsNode, "Views", 0);
            updateCategoryLabel(triggersNode, "Triggers", 0);
            updateCategoryLabel(functionsNode, "Functions", 0);
            updateCategoryLabel(indexesNode, "Indexes", 0);

            tableSelector.refresh();
        });
    }

    /**
     * Sets the database connection and updates the context menu.
     */
    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
        this.tableSelectorContextMenu.setDatabaseConnection(connection);
        if (this.editableTablePane != null) {
            this.editableTablePane.setDatabaseConnection(connection);
        }
    }

    /**
     * Returns the current database connection.
     */
    public DatabaseConnection getDatabaseConnection() {
        return databaseConnection;
    }

    /**
     * Loads data from a table into the editable table pane.
     */
    private void loadTableData(String tableName) {
        if (editableTablePane != null) {
            editableTablePane.loadTableData(databaseConnection, tableName);
        }
    }

    /**
     * Sets the tab pane for the context menu.
     */
    public void setTabPane(TabPane tabPane) {
        this.tableSelectorContextMenu.setTabPane(tabPane);
        this.tabPane = tabPane;
    }

    /**
     * Sets the editable table pane.
     */
    public void setEditableTablePane(EditableTablePane pane) {
        this.editableTablePane = pane;
        if (pane != null) {
            if (databaseConnection != null) {
                pane.setDatabaseConnection(databaseConnection);
            }
            this.tableSelectorContextMenu.setEditableTablePane(pane);
        }
    }

    /**
     * Returns the editable table pane.
     */
    public EditableTablePane getEditableTablePane() {
        return editableTablePane;
    }

    /**
     * Shuts down the refresh executor.
     * Call this when the application is closing.
     */
    public void shutdown() {
        refreshExecutor.shutdown();
        if (editableTablePane != null) {
            editableTablePane.shutdown();
        }
    }
}