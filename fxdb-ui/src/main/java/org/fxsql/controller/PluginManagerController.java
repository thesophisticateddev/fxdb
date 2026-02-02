package org.fxsql.controller;

import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxsql.events.EventBus;
import org.fxsql.plugins.IPlugin;
import org.fxsql.plugins.PluginManager;
import org.fxsql.plugins.events.PluginEvent;
import org.fxsql.plugins.model.PluginInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Controller for the Plugin Manager window.
 * Allows users to browse, install, enable/disable, and manage plugins.
 */
public class PluginManagerController {

    private static final Logger logger = Logger.getLogger(PluginManagerController.class.getName());

    @FXML
    private TableView<PluginInfo> pluginTable;
    @FXML
    private TableColumn<PluginInfo, Boolean> enabledColumn;
    @FXML
    private TableColumn<PluginInfo, String> nameColumn;
    @FXML
    private TableColumn<PluginInfo, String> versionColumn;
    @FXML
    private TableColumn<PluginInfo, String> categoryColumn;
    @FXML
    private TableColumn<PluginInfo, String> statusColumn;
    @FXML
    private TableColumn<PluginInfo, Void> actionsColumn;

    @FXML
    private TextArea descriptionArea;
    @FXML
    private Label authorLabel;
    @FXML
    private Label pluginIdLabel;
    @FXML
    private Label dependenciesLabel;

    @FXML
    private ComboBox<String> categoryFilter;
    @FXML
    private TextField searchField;
    @FXML
    private ProgressIndicator loadingIndicator;
    @FXML
    private Label statusLabel;

    @Inject
    private PluginManager pluginManager;

    private ObservableList<PluginInfo> pluginList;
    private FilteredList<PluginInfo> filteredList;

    @FXML
    public void initialize() {
        setupTable();
        setupFilters();
        setupEventListeners();
        loadPlugins();
    }

    private void setupTable() {
        // Enabled checkbox column
        enabledColumn.setCellValueFactory(cellData -> {
            PluginInfo plugin = cellData.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(plugin.isEnabled());
            property.addListener((obs, oldVal, newVal) -> {
                plugin.setEnabled(newVal);
                pluginManager.saveManifest();
            });
            return property;
        });
        enabledColumn.setCellFactory(CheckBoxTableCell.forTableColumn(enabledColumn));

        // Name column
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getName()));

        // Version column
        versionColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getVersion()));

        // Category column
        categoryColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getCategory()));

        // Status column with colored badges
        statusColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getStatus().name()));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "RUNNING" -> setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 2 8;");
                        case "INSTALLED" -> setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 2 8;");
                        case "ERROR" -> setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-padding: 2 8;");
                        case "LOADING" -> setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-padding: 2 8;");
                        default -> setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-padding: 2 8;");
                    }
                }
            }
        });

        // Actions column with buttons
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button installBtn = new Button("Install");
            private final Button startBtn = new Button("Start");
            private final Button stopBtn = new Button("Stop");
            private final Button uninstallBtn = new Button("Uninstall");
            private final HBox buttons = new HBox(5, installBtn, startBtn, stopBtn, uninstallBtn);

            {
                buttons.setAlignment(Pos.CENTER);
                installBtn.setStyle("-fx-font-size: 10px;");
                startBtn.setStyle("-fx-font-size: 10px;");
                stopBtn.setStyle("-fx-font-size: 10px;");
                uninstallBtn.setStyle("-fx-font-size: 10px;");

                installBtn.setOnAction(e -> {
                    PluginInfo plugin = getTableView().getItems().get(getIndex());
                    onInstallPlugin(plugin);
                });
                startBtn.setOnAction(e -> {
                    PluginInfo plugin = getTableView().getItems().get(getIndex());
                    onStartPlugin(plugin);
                });
                stopBtn.setOnAction(e -> {
                    PluginInfo plugin = getTableView().getItems().get(getIndex());
                    onStopPlugin(plugin);
                });
                uninstallBtn.setOnAction(e -> {
                    PluginInfo plugin = getTableView().getItems().get(getIndex());
                    onUninstallPlugin(plugin);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    PluginInfo plugin = getTableView().getItems().get(getIndex());
                    updateButtonStates(plugin);
                    setGraphic(buttons);
                }
            }

            private void updateButtonStates(PluginInfo plugin) {
                boolean installed = plugin.isInstalled();
                boolean running = plugin.getStatus() == PluginInfo.PluginStatus.RUNNING;

                installBtn.setVisible(!installed);
                installBtn.setManaged(!installed);
                startBtn.setVisible(installed && !running);
                startBtn.setManaged(installed && !running);
                stopBtn.setVisible(running);
                stopBtn.setManaged(running);
                uninstallBtn.setVisible(installed);
                uninstallBtn.setManaged(installed);
            }
        });

        // Selection listener for details panel
        pluginTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        showPluginDetails(newSelection);
                    } else {
                        clearPluginDetails();
                    }
                });
    }

    private void setupFilters() {
        // Category filter
        categoryFilter.setItems(FXCollections.observableArrayList(
                "All", "Database", "Editor", "Tools", "Themes"
        ));
        categoryFilter.setValue("All");
        categoryFilter.setOnAction(e -> applyFilters());

        // Search field
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void setupEventListeners() {
        // Listen for plugin events to update UI
        EventHandler<PluginEvent> handler = event -> Platform.runLater(() -> {
            statusLabel.setText(event.getMessage());
            refreshTable();
        });

        EventBus.addEventHandler(PluginEvent.PLUGIN_INSTALLED, handler);
        EventBus.addEventHandler(PluginEvent.PLUGIN_UNINSTALLED, handler);
        EventBus.addEventHandler(PluginEvent.PLUGIN_STARTED, handler);
        EventBus.addEventHandler(PluginEvent.PLUGIN_STOPPED, handler);
        EventBus.addEventHandler(PluginEvent.PLUGIN_ERROR, handler);
        EventBus.addEventHandler(PluginEvent.PLUGIN_LOADED, handler);
    }

    private void loadPlugins() {
        loadingIndicator.setVisible(true);

        // Load in background thread
        new Thread(() -> {
            pluginManager.loadManifest();

            Platform.runLater(() -> {
                pluginList = FXCollections.observableArrayList(
                        pluginManager.getManifest().getPlugins()
                );
                filteredList = new FilteredList<>(pluginList, p -> true);
                pluginTable.setItems(filteredList);
                loadingIndicator.setVisible(false);
                statusLabel.setText("Loaded " + pluginList.size() + " plugins");
            });
        }).start();
    }

    private void applyFilters() {
        filteredList.setPredicate(plugin -> {
            String category = categoryFilter.getValue();
            String search = searchField.getText().toLowerCase();

            boolean categoryMatch = "All".equals(category) ||
                    plugin.getCategory().equalsIgnoreCase(category);
            boolean searchMatch = search.isEmpty() ||
                    plugin.getName().toLowerCase().contains(search) ||
                    plugin.getDescription().toLowerCase().contains(search);

            return categoryMatch && searchMatch;
        });
    }

    private void showPluginDetails(PluginInfo plugin) {
        descriptionArea.setText(plugin.getDescription());
        authorLabel.setText("Author: " + plugin.getAuthor());
        pluginIdLabel.setText("ID: " + plugin.getId());
        if (plugin.getDependencies() != null && !plugin.getDependencies().isEmpty()) {
            dependenciesLabel.setText("Dependencies: " + String.join(", ", plugin.getDependencies()));
        } else {
            dependenciesLabel.setText("Dependencies: None");
        }
    }

    private void clearPluginDetails() {
        descriptionArea.setText("");
        authorLabel.setText("Author: -");
        pluginIdLabel.setText("ID: -");
        dependenciesLabel.setText("Dependencies: -");
    }

    private void refreshTable() {
        pluginTable.refresh();
    }

    private void onInstallPlugin(PluginInfo plugin) {
        statusLabel.setText("Installing " + plugin.getName() + "...");

        // Check if JAR exists, if not prompt user
        File jarFile = new File("plugins", plugin.getJarFile());
        if (!jarFile.exists()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Plugin JAR Required");
            alert.setHeaderText("Plugin JAR file not found");
            alert.setContentText("The plugin JAR file '" + plugin.getJarFile() +
                    "' was not found in the plugins directory.\n\n" +
                    "Would you like to select the JAR file manually?");

            ButtonType selectButton = new ButtonType("Select JAR");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(selectButton, cancelButton);

            alert.showAndWait().ifPresent(response -> {
                if (response == selectButton) {
                    selectAndCopyJar(plugin);
                }
            });
            return;
        }

        // Install the plugin
        new Thread(() -> {
            boolean success = pluginManager.installPlugin(plugin);
            Platform.runLater(() -> {
                if (success) {
                    statusLabel.setText("Installed: " + plugin.getName());
                } else {
                    statusLabel.setText("Failed to install: " + plugin.getName());
                }
                refreshTable();
            });
        }).start();
    }

    private void selectAndCopyJar(PluginInfo plugin) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Plugin JAR");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JAR Files", "*.jar")
        );

        Stage stage = (Stage) pluginTable.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            try {
                File pluginsDir = new File("plugins");
                if (!pluginsDir.exists()) {
                    pluginsDir.mkdirs();
                }

                File destFile = new File(pluginsDir, plugin.getJarFile());
                Files.copy(selectedFile.toPath(), destFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                // Now install
                onInstallPlugin(plugin);
            } catch (Exception e) {
                logger.severe("Failed to copy JAR: " + e.getMessage());
                statusLabel.setText("Error copying JAR file");
            }
        }
    }

    private void onStartPlugin(PluginInfo plugin) {
        statusLabel.setText("Starting " + plugin.getName() + "...");
        plugin.setStatus(PluginInfo.PluginStatus.LOADING);
        refreshTable();

        new Thread(() -> {
            boolean success = pluginManager.startPlugin(plugin.getId());
            Platform.runLater(() -> {
                if (success) {
                    plugin.setStatus(PluginInfo.PluginStatus.RUNNING);
                    statusLabel.setText("Started: " + plugin.getName());
                } else {
                    plugin.setStatus(PluginInfo.PluginStatus.ERROR);
                    statusLabel.setText("Failed to start: " + plugin.getName());
                }
                refreshTable();
            });
        }).start();
    }

    private void onStopPlugin(PluginInfo plugin) {
        statusLabel.setText("Stopping " + plugin.getName() + "...");

        new Thread(() -> {
            boolean success = pluginManager.stopPlugin(plugin.getId());
            Platform.runLater(() -> {
                if (success) {
                    plugin.setStatus(PluginInfo.PluginStatus.INSTALLED);
                    statusLabel.setText("Stopped: " + plugin.getName());
                } else {
                    statusLabel.setText("Failed to stop: " + plugin.getName());
                }
                refreshTable();
            });
        }).start();
    }

    private void onUninstallPlugin(PluginInfo plugin) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Uninstall Plugin");
        confirm.setHeaderText("Uninstall " + plugin.getName() + "?");
        confirm.setContentText("This will remove the plugin. Are you sure?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    boolean success = pluginManager.uninstallPlugin(plugin.getId());
                    Platform.runLater(() -> {
                        if (success) {
                            statusLabel.setText("Uninstalled: " + plugin.getName());
                        } else {
                            statusLabel.setText("Failed to uninstall: " + plugin.getName());
                        }
                        refreshTable();
                    });
                }).start();
            }
        });
    }

    @FXML
    private void onRefresh() {
        loadPlugins();
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) pluginTable.getScene().getWindow();
        stage.close();
    }
}