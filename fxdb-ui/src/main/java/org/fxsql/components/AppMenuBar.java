package org.fxsql.components;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.fxsql.DatabaseManager;
import org.fxsql.controller.NewConnectionController;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.service.WindowManager;
import org.fxsql.utils.ApplicationTheme;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;


public class AppMenuBar extends MenuBar {

    private DatabaseManager databaseManager;
    private ApplicationTheme currentTheme;
    private DriverDownloader driverDownloader;
    private WindowManager windowManager;

    public AppMenuBar() {
        currentTheme = ApplicationTheme.LIGHT;
        getMenus().addAll(fileMenu(), editMenu(), viewMenu());
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

    private Menu fileMenu() {
        Menu menu = new Menu("_File");
        menu.setMnemonicParsing(true);
        menu.setOnAction(System.out::println);

        var newMenu = createItem("_New", null, new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newMenu.setMnemonicParsing(true);
        newMenu.setOnAction(event -> {
            // Open New Connection window
            openNewConnectionWindow();
        });
        menu.getItems().addAll(newMenu, createItem("Open", Feather.FILE, null), new SeparatorMenuItem(), createItem("Save", Feather.SAVE, new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)));
        return menu;
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
