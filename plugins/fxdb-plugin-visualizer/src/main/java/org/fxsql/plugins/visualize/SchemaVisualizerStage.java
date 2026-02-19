package org.fxsql.plugins.visualize;

import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxsql.DatabaseConnection;
import org.fxsql.DatabaseManager;
import org.fxsql.plugins.runtime.FXPluginRegistry;

import javax.imageio.ImageIO;
import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SchemaVisualizerStage extends Stage {

    private static final Logger logger = Logger.getLogger(SchemaVisualizerStage.class.getName());

    private final SchemaCanvas schemaCanvas;
    private final Label statusLabel;
    private final ComboBox<String> connectionCombo;
    private DatabaseManager databaseManager;

    public SchemaVisualizerStage() {
        setTitle("Schema Visualizer");
        setWidth(1000);
        setHeight(700);

        schemaCanvas = new SchemaCanvas();
        statusLabel = new Label("No connection selected");
        connectionCombo = new ComboBox<>();

        BorderPane root = new BorderPane();
        root.setTop(createToolbar());
        root.setCenter(createCanvasArea());
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root);
        setScene(scene);

        // Resize canvas when window resizes
        scene.widthProperty().addListener((obs, oldVal, newVal) ->
                schemaCanvas.resizeCanvas(newVal.doubleValue(), scene.getHeight() - 80));
        scene.heightProperty().addListener((obs, oldVal, newVal) ->
                schemaCanvas.resizeCanvas(scene.getWidth(), newVal.doubleValue() - 80));

        tryLoadDatabaseManager();
        refreshConnectionList();
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #d0d0d0; -fx-border-width: 0 0 1 0;");

        Label connLabel = new Label("Connection:");
        connectionCombo.setPrefWidth(200);
        connectionCombo.setOnAction(e -> onConnectionSelected());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> onRefresh());

        Separator sep = new Separator();
        sep.setOrientation(javafx.geometry.Orientation.VERTICAL);

        Button zoomInBtn = new Button("+");
        zoomInBtn.setPrefWidth(30);
        zoomInBtn.setOnAction(e -> schemaCanvas.zoomIn());

        Button zoomOutBtn = new Button("-");
        zoomOutBtn.setPrefWidth(30);
        zoomOutBtn.setOnAction(e -> schemaCanvas.zoomOut());

        Button resetBtn = new Button("Reset View");
        resetBtn.setOnAction(e -> schemaCanvas.resetView());

        Separator sep2 = new Separator();
        sep2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        Button exportBtn = new Button("Export PNG");
        exportBtn.setOnAction(e -> onExportPng());

        toolbar.getChildren().addAll(
                connLabel, connectionCombo, refreshBtn,
                sep, zoomInBtn, zoomOutBtn, resetBtn,
                sep2, exportBtn
        );

        return toolbar;
    }

    private StackPane createCanvasArea() {
        StackPane canvasHolder = new StackPane(schemaCanvas);
        canvasHolder.setStyle("-fx-background-color: white;");
        HBox.setHgrow(canvasHolder, Priority.ALWAYS);
        return canvasHolder;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #d0d0d0; -fx-border-width: 1 0 0 0;");
        statusBar.getChildren().add(statusLabel);
        return statusBar;
    }

    private void tryLoadDatabaseManager() {
        try {
            // Get the application's singleton DatabaseManager from the plugin registry
            Object instance = FXPluginRegistry.INSTANCE.get("databaseManager");
            if (instance instanceof DatabaseManager dm) {
                databaseManager = dm;
            } else {
                // Fallback: create new instance and load stored connections
                databaseManager = new DatabaseManager();
                databaseManager.loadStoredConnections();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not load DatabaseManager, connection selector will be empty", e);
        }
    }

    private void refreshConnectionList() {
        List<String> connectionNames = new ArrayList<>();
        if (databaseManager != null) {
            Set<String> names = databaseManager.getConnectionList();
            if (names != null) {
                connectionNames.addAll(names);
            }
        }
        connectionCombo.setItems(FXCollections.observableArrayList(connectionNames));
        if (!connectionNames.isEmpty()) {
            connectionCombo.getSelectionModel().selectFirst();
            onConnectionSelected();
        }
    }

    private void onConnectionSelected() {
        String selected = connectionCombo.getValue();
        if (selected == null || databaseManager == null) {
            statusLabel.setText("No connection selected");
            return;
        }

        statusLabel.setText("Loading schema for: " + selected + "...");

        try {
            DatabaseConnection dbConn = databaseManager.getConnection(selected);
            if (dbConn == null || !dbConn.isConnected()) {
                dbConn = databaseManager.connectByConnectionName(selected);
            }
            if (dbConn != null && dbConn.isConnected()) {
                Connection jdbcConn = dbConn.getConnection();
                schemaCanvas.loadSchema(jdbcConn);
                statusLabel.setText("Connection: " + selected + " | Tables: " + schemaCanvas.getTableCount());
            } else {
                statusLabel.setText("Failed to connect: " + selected);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load schema for connection: " + selected, e);
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void onRefresh() {
        refreshConnectionList();
    }

    private void onExportPng() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Schema as PNG");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        fileChooser.setInitialFileName("schema.png");

        File file = fileChooser.showSaveDialog(this);
        if (file != null) {
            try {
                WritableImage image = schemaCanvas.getCanvas().snapshot(new SnapshotParameters(), null);
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                statusLabel.setText("Exported to: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to export PNG", e);
                statusLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }
}
