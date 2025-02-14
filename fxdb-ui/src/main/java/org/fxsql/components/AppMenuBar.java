package org.fxsql.components;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;


public class AppMenuBar extends MenuBar {

    public AppMenuBar() {
        getMenus().addAll(fileMenu(), editMenu());
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
        menu.getItems().addAll(newMenu, createItem("Open", Feather.FILE, null), new SeparatorMenuItem(),
                createItem("Save", Feather.SAVE, new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)));
        return menu;
    }

    private void openNewConnectionWindow() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("new-connection.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 600, 400);
            Stage stage = new Stage();
            stage.setTitle("New Connection");
            stage.setScene(scene);
            stage.show();

        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Menu editMenu() {
        Menu menu = new Menu("_Edit");
        menu.setMnemonicParsing(true);
        menu.getItems().addAll(createItem("Edit Connection", Feather.EDIT, null),
                createItem("Redo", Feather.CORNER_DOWN_LEFT, null), createItem("Undo", Feather.CORNER_DOWN_RIGHT, null),
                createItem("Edit SQL", null, null));
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
