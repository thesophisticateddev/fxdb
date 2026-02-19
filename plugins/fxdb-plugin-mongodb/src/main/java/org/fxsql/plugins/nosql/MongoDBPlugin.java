package org.fxsql.plugins.nosql;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.event.EventHandler;
import org.fxsql.plugins.AbstractPlugin;
import org.fxsql.plugins.FXPlugin;
import org.fxsql.plugins.runtime.FXPluginRegistry;
import org.fxsql.plugins.ui.PluginUIContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

@FXPlugin(id = "mongodb-connector")
public class MongoDBPlugin extends AbstractPlugin {

    private final MongoConnectionManager connectionManager = new MongoConnectionManager();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MongoDB-Plugin");
        t.setDaemon(true);
        return t;
    });

    private PluginUIContext uiContext;
    private TreeItem<String> mongoRoot;
    private EventHandler<MouseEvent> clickHandler;
    private final List<Tab> openTabs = new ArrayList<>();

    @Override
    public String getId() {
        return "mongodb-connector";
    }

    @Override
    public String getName() {
        return "MongoDB Connector";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    protected void onInitialize() {
        logger.info("MongoDB Connector plugin initialized");
    }

    @Override
    protected void onStart() {
        logger.info("MongoDB Connector plugin starting");
        Platform.runLater(() -> {
            try {
                uiContext = (PluginUIContext) FXPluginRegistry.INSTANCE.get("ui.context");
                if (uiContext == null) {
                    logger.severe("PluginUIContext not found in registry");
                    return;
                }

                // Create MongoDB root node in the browser tree
                mongoRoot = new TreeItem<>("MongoDB");
                mongoRoot.setExpanded(true);
                mongoRoot.getChildren().add(new TreeItem<>("(not connected)"));
                uiContext.addBrowserNode(mongoRoot);

                // Set up right-click context menu on MongoDB nodes
                setupContextMenu();

                // Set up double-click handler for collection nodes
                setupClickHandler();

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to start MongoDB Connector", e);
            }
        });
    }

    @Override
    protected void onStop() {
        logger.info("MongoDB Connector plugin stopping");
        Platform.runLater(() -> {
            // Remove click handler
            if (clickHandler != null && uiContext != null) {
                uiContext.getTreeView().removeEventHandler(MouseEvent.MOUSE_CLICKED, clickHandler);
            }

            // Close all open tabs
            for (Tab tab : new ArrayList<>(openTabs)) {
                if (tab.getContent() instanceof MongoDocumentPane pane) {
                    pane.shutdown();
                }
                uiContext.removeTab(tab);
            }
            openTabs.clear();

            // Remove MongoDB tree node
            if (uiContext != null && mongoRoot != null) {
                uiContext.removeBrowserNode(mongoRoot);
            }

            // Disconnect
            connectionManager.disconnect();
            executor.shutdownNow();
        });
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem connectItem = new MenuItem("Connect...");
        connectItem.setOnAction(e -> showConnectionDialog());

        MenuItem disconnectItem = new MenuItem("Disconnect");
        disconnectItem.setOnAction(e -> disconnect());

        MenuItem refreshItem = new MenuItem("Refresh");
        refreshItem.setOnAction(e -> refreshDatabases());

        contextMenu.getItems().addAll(connectItem, disconnectItem, new SeparatorMenuItem(), refreshItem);

        TreeView<String> treeView = uiContext.getTreeView();
        treeView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null && isMongoNode(selected)) {
                    // Update menu state
                    boolean connected = connectionManager.isConnected();
                    connectItem.setDisable(connected);
                    disconnectItem.setDisable(!connected);
                    refreshItem.setDisable(!connected);

                    contextMenu.show(treeView, event.getScreenX(), event.getScreenY());
                    event.consume();
                }
            }
        });
    }

    private void setupClickHandler() {
        clickHandler = event -> {
            if (event.getClickCount() != 2 || event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            TreeView<String> treeView = uiContext.getTreeView();
            TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            // Check if this is a collection node (grandchild of mongoRoot)
            TreeItem<String> parent = selected.getParent();
            if (parent == null) return;

            TreeItem<String> grandparent = parent.getParent();
            if (grandparent == mongoRoot) {
                // This is a collection node: parent = database, grandparent = mongoRoot
                String dbName = parent.getValue();
                String collName = selected.getValue();
                openCollectionTab(dbName, collName);
                event.consume();
            }
        };

        uiContext.getTreeView().addEventHandler(MouseEvent.MOUSE_CLICKED, clickHandler);
    }

    /**
     * Checks whether the given tree item is part of the MongoDB subtree.
     */
    private boolean isMongoNode(TreeItem<String> item) {
        TreeItem<String> current = item;
        while (current != null) {
            if (current == mongoRoot) return true;
            current = current.getParent();
        }
        return false;
    }

    private void showConnectionDialog() {
        MongoConnectionDialog dialog = new MongoConnectionDialog();
        Optional<MongoConnectionDialog.ConnectionParams> result = dialog.showAndWait();
        result.ifPresent(this::connectWithParams);
    }

    private void connectWithParams(MongoConnectionDialog.ConnectionParams params) {
        Task<Void> connectTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                connectionManager.connect(
                        params.host(), params.port(), params.database(),
                        params.username().isEmpty() ? null : params.username(),
                        params.password().isEmpty() ? null : params.password(),
                        params.authDatabase()
                );
                return null;
            }
        };

        connectTask.setOnSucceeded(e -> Platform.runLater(() -> {
            mongoRoot.setValue("MongoDB (" + params.host() + ":" + params.port() + ")");
            refreshDatabases();
        }));

        connectTask.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = connectTask.getException();
            logger.log(Level.WARNING, "MongoDB connection failed", ex);
            mongoRoot.getChildren().clear();
            mongoRoot.getChildren().add(new TreeItem<>("Connection failed: "
                    + (ex != null ? ex.getMessage() : "Unknown error")));
        }));

        mongoRoot.getChildren().clear();
        mongoRoot.getChildren().add(new TreeItem<>("Connecting..."));
        executor.submit(connectTask);
    }

    private void disconnect() {
        connectionManager.disconnect();

        // Close all MongoDB tabs
        Platform.runLater(() -> {
            for (Tab tab : new ArrayList<>(openTabs)) {
                if (tab.getContent() instanceof MongoDocumentPane pane) {
                    pane.shutdown();
                }
                uiContext.removeTab(tab);
            }
            openTabs.clear();

            mongoRoot.setValue("MongoDB");
            mongoRoot.getChildren().clear();
            mongoRoot.getChildren().add(new TreeItem<>("(not connected)"));
        });
    }

    private void refreshDatabases() {
        if (!connectionManager.isConnected()) return;

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return connectionManager.listDatabaseNames();
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            List<String> databases = task.getValue();
            mongoRoot.getChildren().clear();

            for (String dbName : databases) {
                TreeItem<String> dbItem = new TreeItem<>(dbName);
                dbItem.setExpanded(false);

                // Lazy-load collections when database node is expanded
                dbItem.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                    if (isExpanded && dbItem.getChildren().size() == 1
                            && "Loading...".equals(dbItem.getChildren().get(0).getValue())) {
                        loadCollections(dbItem, dbName);
                    }
                });

                // Placeholder so the expand arrow shows
                dbItem.getChildren().add(new TreeItem<>("Loading..."));
                mongoRoot.getChildren().add(dbItem);
            }

            if (databases.isEmpty()) {
                mongoRoot.getChildren().add(new TreeItem<>("(no databases)"));
            }
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            mongoRoot.getChildren().clear();
            mongoRoot.getChildren().add(new TreeItem<>("Error loading databases"));
            logger.log(Level.WARNING, "Failed to load databases", task.getException());
        }));

        executor.submit(task);
    }

    private void loadCollections(TreeItem<String> dbItem, String databaseName) {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return connectionManager.listCollectionNames(databaseName);
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            dbItem.getChildren().clear();
            List<String> collections = task.getValue();
            for (String collName : collections) {
                dbItem.getChildren().add(new TreeItem<>(collName));
            }
            if (collections.isEmpty()) {
                dbItem.getChildren().add(new TreeItem<>("(no collections)"));
            }
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            dbItem.getChildren().clear();
            dbItem.getChildren().add(new TreeItem<>("Error loading collections"));
        }));

        executor.submit(task);
    }

    private void openCollectionTab(String dbName, String collName) {
        // Check if tab already exists
        String tabTitle = dbName + "." + collName;
        for (Tab tab : openTabs) {
            if (tabTitle.equals(tab.getText())) {
                uiContext.getTabPane().getSelectionModel().select(tab);
                return;
            }
        }

        // Create new document pane tab
        MongoDocumentPane docPane = new MongoDocumentPane(connectionManager, dbName, collName);

        Tab tab = new Tab(tabTitle);
        tab.setContent(docPane);
        tab.setOnClosed(event -> {
            docPane.shutdown();
            openTabs.remove(tab);
        });

        openTabs.add(tab);
        uiContext.addTab(tab);
    }
}
