package org.fxsql.plugins.nosql;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.bson.Document;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pane that displays MongoDB documents in a TreeTableView.
 * Used as Tab content when a collection is opened from the browser tree.
 */
public class MongoDocumentPane extends BorderPane {

    private static final Logger logger = Logger.getLogger(MongoDocumentPane.class.getName());
    private static final int DEFAULT_DOCUMENT_LIMIT = 50;

    private final MongoConnectionManager connectionManager;
    private final String databaseName;
    private final String collectionName;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MongoDB-DocPane");
        t.setDaemon(true);
        return t;
    });

    private final TreeTableView<BsonTreeTableModel.BsonField> documentView = new TreeTableView<>();
    private final TreeItem<BsonTreeTableModel.BsonField> documentRoot =
            new TreeItem<>(new BsonTreeTableModel.BsonField("Documents", "", ""));

    private final Label statusLabel = new Label();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Spinner<Integer> limitSpinner = new Spinner<>(10, 1000, DEFAULT_DOCUMENT_LIMIT, 10);

    public MongoDocumentPane(MongoConnectionManager connectionManager,
                             String databaseName, String collectionName) {
        this.connectionManager = connectionManager;
        this.databaseName = databaseName;
        this.collectionName = collectionName;

        setTop(createToolbar());
        setCenter(createDocumentView());
        setBottom(createStatusBar());

        loadDocuments();
    }

    @SuppressWarnings("unchecked")
    private TreeTableView<BsonTreeTableModel.BsonField> createDocumentView() {
        documentView.setRoot(documentRoot);
        documentView.setShowRoot(false);
        documentRoot.setExpanded(true);
        documentView.setPlaceholder(new Label("Loading documents..."));

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

        return documentView;
    }

    private String getTypeStyle(String type) {
        return switch (type) {
            case "String"            -> "-fx-text-fill: #2da44e;";
            case "Integer", "Double" -> "-fx-text-fill: #0969da;";
            case "Boolean"           -> "-fx-text-fill: #bf8700;";
            case "Null"              -> "-fx-text-fill: #656d76; -fx-font-style: italic;";
            case "ObjectId"          -> "-fx-text-fill: #cf222e;";
            case "Date"              -> "-fx-text-fill: #8250df;";
            case "Document", "Array" -> "-fx-font-weight: bold;";
            default                  -> "";
        };
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setPadding(new Insets(6));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: -color-bg-subtle; "
                + "-fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        Label limitLabel = new Label("Limit:");
        limitSpinner.setPrefWidth(80);
        limitSpinner.setEditable(true);

        Button reloadBtn = new Button("Reload");
        reloadBtn.setOnAction(e -> loadDocuments());

        progressIndicator.setPrefSize(16, 16);
        progressIndicator.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(limitLabel, limitSpinner, reloadBtn, spacer, progressIndicator);
        return toolbar;
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

    private void loadDocuments() {
        progressIndicator.setVisible(true);
        int limit = limitSpinner.getValue();
        statusLabel.setText("Loading " + databaseName + "." + collectionName + "...");

        Task<List<Document>> task = new Task<>() {
            @Override
            protected List<Document> call() {
                return connectionManager.findDocuments(databaseName, collectionName, limit);
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

            progressIndicator.setVisible(false);

            // Count total documents in background
            Task<Long> countTask = new Task<>() {
                @Override
                protected Long call() {
                    return connectionManager.countDocuments(databaseName, collectionName);
                }
            };
            countTask.setOnSucceeded(ce -> Platform.runLater(() ->
                    statusLabel.setText(String.format("%s.%s | Showing %d of %d documents",
                            databaseName, collectionName, docs.size(), countTask.getValue()))
            ));
            countTask.setOnFailed(ce -> Platform.runLater(() ->
                    statusLabel.setText(String.format("%s.%s | Showing %d documents",
                            databaseName, collectionName, docs.size()))
            ));
            executor.submit(countTask);
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            Throwable ex = task.getException();
            statusLabel.setText("Failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            logger.log(Level.WARNING, "Failed to load documents", ex);
        }));

        executor.submit(task);
    }

    /**
     * Shuts down the background executor. Call when the tab is closed.
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
