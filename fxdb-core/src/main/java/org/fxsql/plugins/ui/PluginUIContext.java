package org.fxsql.plugins.ui;

import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * Provides plugins with access to the main application UI components.
 * Plugins retrieve this from FXPluginRegistry using the key "ui.context".
 */
public class PluginUIContext {

    private final TabPane tabPane;
    private final TreeView<String> treeView;

    public PluginUIContext(TabPane tabPane, TreeView<String> treeView) {
        this.tabPane = tabPane;
        this.treeView = treeView;
    }

    public TabPane getTabPane() {
        return tabPane;
    }

    public TreeView<String> getTreeView() {
        return treeView;
    }

    /**
     * Adds a tab to the main tab pane and selects it.
     * Safe to call from any thread.
     */
    public void addTab(Tab tab) {
        if (Platform.isFxApplicationThread()) {
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
        } else {
            Platform.runLater(() -> {
                tabPane.getTabs().add(tab);
                tabPane.getSelectionModel().select(tab);
            });
        }
    }

    /**
     * Removes a tab from the main tab pane.
     * Safe to call from any thread.
     */
    public void removeTab(Tab tab) {
        if (Platform.isFxApplicationThread()) {
            tabPane.getTabs().remove(tab);
        } else {
            Platform.runLater(() -> tabPane.getTabs().remove(tab));
        }
    }

    /**
     * Adds a top-level node to the browser tree root.
     * Safe to call from any thread.
     */
    public void addBrowserNode(TreeItem<String> node) {
        if (Platform.isFxApplicationThread()) {
            TreeItem<String> root = treeView.getRoot();
            if (root != null) {
                root.getChildren().add(node);
            }
        } else {
            Platform.runLater(() -> {
                TreeItem<String> root = treeView.getRoot();
                if (root != null) {
                    root.getChildren().add(node);
                }
            });
        }
    }

    /**
     * Removes a top-level node from the browser tree root.
     * Safe to call from any thread.
     */
    public void removeBrowserNode(TreeItem<String> node) {
        if (Platform.isFxApplicationThread()) {
            TreeItem<String> root = treeView.getRoot();
            if (root != null) {
                root.getChildren().remove(node);
            }
        } else {
            Platform.runLater(() -> {
                TreeItem<String> root = treeView.getRoot();
                if (root != null) {
                    root.getChildren().remove(node);
                }
            });
        }
    }
}
