package org.fxsql.services;

import atlantafx.base.theme.Tweaks;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import org.fxsql.DatabaseConnection;
import org.fxsql.DatabaseObjects;
import org.fxsql.components.TableContextMenu;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

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

    private final TableView<ObservableList<Object>> tableView;
    private final TreeView<String> tableSelector;
    private final TableContextMenu tableSelectorContextMenu;
    private final TableInteractionService tableInteractionService;
    private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor();

    private TabPane tabPane;
    private DatabaseConnection databaseConnection;
    private volatile boolean isRefreshing = false;

    // Tree root items for each category
    private TreeItem<String> rootItem;
    private TreeItem<String> tablesNode;
    private TreeItem<String> viewsNode;
    private TreeItem<String> triggersNode;
    private TreeItem<String> functionsNode;
    private TreeItem<String> indexesNode;

    public DynamicSQLView(TableView<ObservableList<Object>> tbv, TreeView<String> tableSelector,
                          DatabaseConnection connection) {
        this.tableView = tbv;
        this.tableSelector = tableSelector;
        this.tableSelector.getStyleClass().add(Tweaks.ALT_ICON);
        this.databaseConnection = connection;
        this.tableSelectorContextMenu = new TableContextMenu(this.databaseConnection, this.tableView, this.tableSelector);
        this.tableInteractionService = new TableInteractionService(tableView);

        initializeTreeStructure();
        setupEventHandlers();
    }

    public DynamicSQLView(TableView<ObservableList<Object>> tbv, TreeView<String> tableSelector) {
        this.tableView = tbv;
        this.tableSelector = tableSelector;
        this.tableSelector.getStyleClass().add(Tweaks.ALT_ICON);
        this.tableSelectorContextMenu = new TableContextMenu(this.databaseConnection, this.tableView, this.tableSelector);
        this.tableInteractionService = new TableInteractionService(tableView);

        initializeTreeStructure();
        setupEventHandlers();
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
                    // Don't show context menu for category nodes
                    return;
                }
                this.tableSelectorContextMenu.showContextMenu(databaseConnection, event);
            }

            // Left click to hide context menu
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                this.tableSelectorContextMenu.hide();
            }
        });
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

        // Only load data for Tables and Views
        if ("Tables".equals(parentCategory) || "Views".equals(parentCategory)) {
            loadTableData(itemName);
        }
    }

    /**
     * Checks if the given item is a category node.
     */
    private boolean isCategoryNode(TreeItem<String> item) {
        return item == tablesNode || item == viewsNode ||
               item == triggersNode || item == functionsNode ||
               item == indexesNode || item == rootItem;
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
    }

    /**
     * Returns the current database connection.
     */
    public DatabaseConnection getDatabaseConnection() {
        return databaseConnection;
    }

    /**
     * Loads data from a table into the table view.
     */
    private void loadTableData(String tableName) {
        this.tableInteractionService.loadTableData(databaseConnection, tableName);
    }

    /**
     * Sets the tab pane for the context menu.
     */
    public void setTabPane(TabPane tabPane) {
        this.tableSelectorContextMenu.setTabPane(tabPane);
        this.tabPane = tabPane;
    }

    /**
     * Shuts down the refresh executor.
     * Call this when the application is closing.
     */
    public void shutdown() {
        refreshExecutor.shutdown();
    }
}