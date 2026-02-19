package org.fxsql.plugins.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;

/**
 * Provides plugins with access to the main application UI components.
 * Plugins retrieve this from FXPluginRegistry using the key "ui.context".
 */
public class PluginUIContext {

    private final TabPane tabPane;
    private final TreeView<String> pluginTreeView;
    private final Separator separator;
    private final HBox header;

    public PluginUIContext(TabPane tabPane, TreeView<String> pluginTreeView,
                           Separator separator, HBox header) {
        this.tabPane = tabPane;
        this.pluginTreeView = pluginTreeView;
        this.separator = separator;
        this.header = header;
    }

    public TabPane getTabPane() {
        return tabPane;
    }

    public TreeView<String> getTreeView() {
        return pluginTreeView;
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
     * Adds a top-level node to the plugin browser tree.
     * Automatically shows the plugin browser section if hidden.
     * Safe to call from any thread.
     */
    public void addBrowserNode(TreeItem<String> node) {
        if (Platform.isFxApplicationThread()) {
            doAddBrowserNode(node);
        } else {
            Platform.runLater(() -> doAddBrowserNode(node));
        }
    }

    private void doAddBrowserNode(TreeItem<String> node) {
        TreeItem<String> root = pluginTreeView.getRoot();
        if (root != null) {
            root.getChildren().add(node);
            showPluginBrowser();
        }
    }

    /**
     * Removes a top-level node from the plugin browser tree.
     * Automatically hides the plugin browser section if no nodes remain.
     * Safe to call from any thread.
     */
    public void removeBrowserNode(TreeItem<String> node) {
        if (Platform.isFxApplicationThread()) {
            doRemoveBrowserNode(node);
        } else {
            Platform.runLater(() -> doRemoveBrowserNode(node));
        }
    }

    private void doRemoveBrowserNode(TreeItem<String> node) {
        TreeItem<String> root = pluginTreeView.getRoot();
        if (root != null) {
            root.getChildren().remove(node);
            if (root.getChildren().isEmpty()) {
                hidePluginBrowser();
            }
        }
    }

    private void showPluginBrowser() {
        setVisible(separator, true);
        setVisible(header, true);
        setVisible(pluginTreeView, true);
    }

    private void hidePluginBrowser() {
        setVisible(separator, false);
        setVisible(header, false);
        setVisible(pluginTreeView, false);
    }

    private void setVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
