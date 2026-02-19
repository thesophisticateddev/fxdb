package org.fxsql.plugins.nosql;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.bson.Document;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MongoDBStage extends Stage {

    private static final Logger logger = Logger.getLogger(MongoDBStage.class.getName());
    private static final int DEFAULT_DOCUMENT_LIMIT = 50;

    private final MongoConnectionManager connectionManager = new MongoConnectionManager();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MongoDB-Worker");
        t.setDaemon(true);
        return t;
    });

    // Left panel: database/collection browser
    private final TreeView<String> browserTree = new TreeView<>();
    private TreeItem<String> browserRoot;

    // Right panel: document viewer
    private final TreeTableView<BsonTreeTableModel.BsonField> documentView = new TreeTableView<>();
    private final TreeItem<BsonTreeTableModel.BsonField> documentRoot =
            new TreeItem<>(new BsonTreeTableModel.BsonField("Documents", "", ""));

    // Status
    private final Label statusLabel = new Label("Not connected");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Spinner<Integer> limitSpinner = new Spinner<>(10, 1000, DEFAULT_DOCUMENT_LIMIT, 10);

    // Current selection state
    private String selectedDatabase;
    private String selectedCollection;

    public MongoDBStage() {
        setTitle("MongoDB Connector");
        setWidth(1200);
        setHeight(750);

        BorderPane root = new BorderPane();
        root.setTop(createToolbar());
        root.setCenter(createMainContent());
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root);
        setScene(scene);

        setupDocumentViewColumns();
        setupBrowserTreeEvents();

        setOnCloseRequest(event -> shutdown());
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: -color-bg-subtle; "
                + "-fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        Button connectBtn = new Button("Connect...");
        connectBtn.setOnAction(e -> showConnectionDialog());

        Button disconnectBtn = new Button("Disconnect");
        disconnectBtn.setOnAction(e -> disconnect());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshBrowser());

        Separator sep = new Separator(Orientation.VERTICAL);

        Label limitLabel = new Label("Doc limit:");
        limitSpinner.setPrefWidth(80);
        limitSpinner.setEditable(true);

        Button reloadDocsBtn = new Button("Reload Documents");
        reloadDocsBtn.setOnAction(e -> {
            if (selectedDatabase != null && selectedCollection != null) {
                loadDocuments(selectedDatabase, selectedCollection);
            }
        });

        progressIndicator.setPrefSize(18, 18);
        progressIndicator.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                connectBtn, disconnectBtn, refreshBtn,
                sep, limitLabel, limitSpinner, reloadDocsBtn,
                spacer, progressIndicator
        );

        return toolbar;
    }

    private SplitPane createMainContent() {
        // Left: database/collection browser
        browserRoot = new TreeItem<>("(not connected)");
        browserRoot.setExpanded(true);
        browserTree.setRoot(browserRoot);
        browserTree.setShowRoot(true);

        VBox leftPane = new VBox(browserTree);
        VBox.setVgrow(browserTree, Priority.ALWAYS);
        leftPane.setPrefWidth(250);
        leftPane.setMinWidth(180);

        // Right: document TreeTableView
        documentView.setRoot(documentRoot);
        documentView.setShowRoot(false);
        documentRoot.setExpanded(true);
        documentView.setPlaceholder(new Label("Select a collection to view documents"));

        VBox rightPane = new VBox(documentView);
        VBox.setVgrow(documentView, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setDividerPositions(0.25);

        return splitPane;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(8);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: -color-bg-subtle; "
                + "-fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");
        statusBar.getChildren().add(statusLabel);
        return statusBar;
    }

    @SuppressWarnings("unchecked")
    private void setupDocumentViewColumns() {
        TreeTableColumn<BsonTreeTableModel.BsonField, String> keyCol = new TreeTableColumn<>("Key");
        keyCol.setCellValueFactory(param ->
                new SimpleStringProperty(param.getValue().getValue().key()));
        keyCol.setPrefWidth(250);

        TreeTableColumn<BsonTreeTableModel.BsonField, String> valueCol = new TreeTableColumn<>("Value");
        valueCol.setCellValueFactory(param ->
                new SimpleStringProperty(param.getValue().getValue().value()));
        valueCol.setPrefWidth(400);

        TreeTableColumn<BsonTreeTableModel.BsonField, String> typeCol = new TreeTableColumn<>("Type");
        typeCol.setCellValueFactory(param ->
                new SimpleStringProperty(param.getValue().getValue().type()));
        typeCol.setPrefWidth(120);

        typeCol.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle(getTypeStyle(item));
                }
            }
        });

        documentView.getColumns().addAll(keyCol, valueCol, typeCol);
        documentView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
    }

    private String getTypeStyle(String type) {
        return switch (type) {
            case "String"            -> "-fx-text-fill: #2da44e;";   // green
            case "Integer", "Double" -> "-fx-text-fill: #0969da;";   // blue
            case "Boolean"           -> "-fx-text-fill: #bf8700;";   // amber
            case "Null"              -> "-fx-text-fill: #656d76; -fx-font-style: italic;";
            case "ObjectId"          -> "-fx-text-fill: #cf222e;";   // red
            case "Date"              -> "-fx-text-fill: #8250df;";   // purple
            case "Document", "Array" -> "-fx-font-weight: bold;";
            default                  -> "";
        };
    }

    private void setupBrowserTreeEvents() {
        browserTree.setOnMouseClicked(event -> {
            TreeItem<String> selected = browserTree.getSelectionModel().getSelectedItem();
            if (selected == null || event.getClickCount() != 2) return;

            TreeItem<String> parent = selected.getParent();
            if (parent != null && parent != browserRoot) {
                // This is a collection node (parent is a database node)
                selectedDatabase = parent.getValue();
                selectedCollection = selected.getValue();
                loadDocuments(selectedDatabase, selectedCollection);
            }
        });
    }

    // --- Connection management ---

    private void showConnectionDialog() {
        MongoConnectionDialog dialog = new MongoConnectionDialog();
        dialog.initOwner(this);
        Optional<MongoConnectionDialog.ConnectionParams> result = dialog.showAndWait();
        result.ifPresent(this::connectWithParams);
    }

    private void connectWithParams(MongoConnectionDialog.ConnectionParams params) {
        setLoading(true);
        statusLabel.setText("Connecting to " + params.host() + ":" + params.port() + "...");

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
            setLoading(false);
            statusLabel.setText("Connected to " + params.host() + ":" + params.port());
            refreshBrowser();
        }));

        connectTask.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            Throwable ex = connectTask.getException();
            statusLabel.setText("Connection failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            logger.log(Level.WARNING, "MongoDB connection failed", ex);
        }));

        executor.submit(connectTask);
    }

    private void disconnect() {
        connectionManager.disconnect();
        browserRoot.getChildren().clear();
        browserRoot.setValue("(not connected)");
        documentRoot.getChildren().clear();
        selectedDatabase = null;
        selectedCollection = null;
        statusLabel.setText("Disconnected");
    }

    // --- Browser tree ---

    private void refreshBrowser() {
        if (!connectionManager.isConnected()) {
            statusLabel.setText("Not connected");
            return;
        }

        setLoading(true);
        statusLabel.setText("Loading databases...");

        Task<List<String>> loadTask = new Task<>() {
            @Override
            protected List<String> call() {
                return connectionManager.listDatabaseNames();
            }
        };

        loadTask.setOnSucceeded(e -> Platform.runLater(() -> {
            List<String> databases = loadTask.getValue();
            browserRoot.getChildren().clear();
            browserRoot.setValue("MongoDB");
            browserRoot.setExpanded(true);

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
                browserRoot.getChildren().add(dbItem);
            }

            setLoading(false);
            statusLabel.setText("Loaded " + databases.size() + " databases");
        }));

        loadTask.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            Throwable ex = loadTask.getException();
            statusLabel.setText("Failed to load databases: " + (ex != null ? ex.getMessage() : ""));
            logger.log(Level.WARNING, "Failed to load databases", ex);
        }));

        executor.submit(loadTask);
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

    // --- Document loading and display ---

    private void loadDocuments(String database, String collection) {
        setLoading(true);
        int limit = limitSpinner.getValue();
        statusLabel.setText("Loading documents from " + database + "." + collection + "...");

        Task<List<Document>> task = new Task<>() {
            @Override
            protected List<Document> call() {
                return connectionManager.findDocuments(database, collection, limit);
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            List<Document> docs = task.getValue();
            documentRoot.getChildren().clear();

            for (int i = 0; i < docs.size(); i++) {
                TreeItem<BsonTreeTableModel.BsonField> docItem =
                        BsonTreeTableModel.documentToTreeItem(docs.get(i), i);
                documentRoot.getChildren().add(docItem);
            }

            setLoading(false);

            // Count total documents in a background task
            Task<Long> countTask = new Task<>() {
                @Override
                protected Long call() {
                    return connectionManager.countDocuments(database, collection);
                }
            };
            countTask.setOnSucceeded(ce -> Platform.runLater(() ->
                    statusLabel.setText(String.format("%s.%s | Showing %d of %d documents",
                            database, collection, docs.size(), countTask.getValue()))
            ));
            countTask.setOnFailed(ce -> Platform.runLater(() ->
                    statusLabel.setText(String.format("%s.%s | Showing %d documents",
                            database, collection, docs.size()))
            ));
            executor.submit(countTask);
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            Throwable ex = task.getException();
            statusLabel.setText("Failed to load documents: " + (ex != null ? ex.getMessage() : ""));
            logger.log(Level.WARNING, "Failed to load documents", ex);
        }));

        executor.submit(task);
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
    }

    private void shutdown() {
        connectionManager.disconnect();
        executor.shutdownNow();
    }
}
