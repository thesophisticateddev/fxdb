package org.fxsql.components;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxsql.DatabaseManager;
import org.fxsql.controller.NewConnectionController;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.service.WindowManager;
import org.fxsql.utils.ApplicationTheme;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;


public class AppMenuBar extends MenuBar {

    private DatabaseManager databaseManager;
    private ApplicationTheme currentTheme;
    private DriverDownloader driverDownloader;
    private WindowManager windowManager;
    private Consumer<File> onOpenSqlFile;

    public AppMenuBar() {
        currentTheme = ApplicationTheme.LIGHT;
        getMenus().addAll(fileMenu(), editMenu(), viewMenu(), toolsMenu());
    }

    public void setDriverDownloader(DriverDownloader d) {
        this.driverDownloader = d;
    }

    public void setDatabaseManager(DatabaseManager dm) {
        databaseManager = dm;
    }

    public void setWindowManager(WindowManager windowManager) {
        this.windowManager = windowManager;
    }

    /**
     * Sets a callback to be invoked when the user opens an SQL file.
     */
    public void setOnOpenSqlFile(Consumer<File> callback) {
        this.onOpenSqlFile = callback;
    }

    private Menu fileMenu() {
        Menu menu = new Menu("_File");
        menu.setMnemonicParsing(true);

        // New Connection
        var newConnectionItem = createItem("New _Connection", Feather.PLUS_CIRCLE, new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newConnectionItem.setMnemonicParsing(true);
        newConnectionItem.setOnAction(event -> openNewConnectionWindow());

        // Open SQL File
        var openSqlItem = createItem("_Open SQL File...", Feather.FOLDER, new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openSqlItem.setMnemonicParsing(true);
        openSqlItem.setOnAction(event -> openSqlFile());

        // Exit
        var exitItem = createItem("E_xit", Feather.LOG_OUT, new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
        exitItem.setMnemonicParsing(true);
        exitItem.setOnAction(event -> {
            Stage stage = (Stage) this.getScene().getWindow();
            stage.close();
        });

        menu.getItems().addAll(
                newConnectionItem,
                new SeparatorMenuItem(),
                openSqlItem,
                new SeparatorMenuItem(),
                exitItem
        );
        return menu;
    }

    private void openSqlFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open SQL File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("SQL Files", "*.sql"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (file != null && onOpenSqlFile != null) {
            onOpenSqlFile.accept(file);
        }
    }

    private void openNewConnectionWindow() {
        try {
            WindowManager.WindowResult<NewConnectionController> result = windowManager.loadWindow("new-connection.fxml");

            Scene scene = new Scene(result.root);
            Stage stage = new Stage();
            stage.setTitle("New Connection");
            stage.setScene(scene);

            // Set minimum size to ensure all elements are visible
            stage.setMinWidth(500);
            stage.setMinHeight(450);

            // Set preferred size
            stage.setWidth(600);
            stage.setHeight(500);

            // Allow resizing
            stage.setResizable(true);

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Menu editMenu() {
        Menu menu = new Menu("_Edit");
        menu.setMnemonicParsing(true);
        menu.getItems().addAll(createItem("Edit Connection", Feather.EDIT, null), createItem("Redo", Feather.CORNER_DOWN_LEFT, null), createItem("Undo", Feather.CORNER_DOWN_RIGHT, null), createItem("Edit SQL", null, null));
        return menu;
    }

    private Menu viewMenu() {
        Menu menu = new Menu("_View");
        menu.setMnemonicParsing(true);

        //Toggle theme Item
        CheckMenuItem toggleTheme = new CheckMenuItem("Toggle Dark Theme", new FontIcon(Feather.EYE));
        toggleTheme.setSelected(false);
        toggleTheme.selectedProperty().addListener((obs, old, val) -> {
            if (currentTheme == ApplicationTheme.LIGHT) {
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                currentTheme = ApplicationTheme.DARK;
            } else {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                currentTheme = ApplicationTheme.LIGHT;
            }
        });
        menu.getItems().add(toggleTheme);
        return menu;
    }

    private Menu toolsMenu() {
        Menu menu = new Menu("_Tools");
        menu.setMnemonicParsing(true);

        // Plugin Manager menu item
        MenuItem pluginManager = createItem("Plugin Manager", Feather.PACKAGE,
                new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        pluginManager.setOnAction(event -> openPluginManagerWindow());

        // Driver Manager menu item
        MenuItem driverManager = createItem("Driver Manager", Feather.HARD_DRIVE, null);
        driverManager.setOnAction(event -> {
            // TODO: Open driver manager
        });

        menu.getItems().addAll(pluginManager, new SeparatorMenuItem(), driverManager);
        return menu;
    }

    private void openPluginManagerWindow() {
        try {
            WindowManager.WindowResult<?> result = windowManager.loadWindow("plugin-manager.fxml");

            Scene scene = new Scene(result.root);
            Stage stage = new Stage();
            stage.setTitle("Plugin Manager");
            stage.setScene(scene);

            stage.setMinWidth(800);
            stage.setMinHeight(500);
            stage.setWidth(900);
            stage.setHeight(600);
            stage.setResizable(true);

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to open Plugin Manager");
            alert.setContentText(e.getMessage());
            alert.show();
        }
    }

    private MenuItem createItem(String text, Ikon icon, KeyCombination accelerator) {
        var item = new MenuItem(text);
        if (icon != null) {
            item.setGraphic(new FontIcon(icon));
        }
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        return item;
    }

}
