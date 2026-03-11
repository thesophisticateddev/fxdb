package org.fxsql.dock;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.dockfx.DockNode;
import org.dockfx.DockPane;
import org.dockfx.DockPos;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Dock node for plugins. Contains a TabPane so each plugin gets its own tab.
 * <p>
 * The hidden pluginBrowser TreeView is still passed to PluginUIContext (SDK contract).
 * When a plugin calls addBrowserNode(), a listener here intercepts the addition and
 * creates a dedicated tab with its own TreeView inside this dock's TabPane.
 */
public class PluginDockNode {

    private final DockNode dockNode;
    private final TabPane pluginTabPane;
    private final TreeView<String> pluginBrowser;
    private final Separator pluginBrowserSeparator;
    private final HBox pluginBrowserHeader;

    public PluginDockNode() {
        // These are passed to PluginUIContext to satisfy the SDK contract.
        // The separator and header are no longer shown directly — the listener
        // below creates tabs instead.
        pluginBrowserSeparator = new Separator();
        pluginBrowserSeparator.setVisible(false);
        pluginBrowserSeparator.setManaged(false);

        pluginBrowserHeader = new HBox();
        pluginBrowserHeader.setVisible(false);
        pluginBrowserHeader.setManaged(false);

        // Hidden bridge TreeView — plugins add nodes here via PluginUIContext.addBrowserNode()
        pluginBrowser = new TreeView<>();
        pluginBrowser.setVisible(false);
        pluginBrowser.setManaged(false);

        // The actual visible UI: a TabPane where each plugin gets its own tab
        pluginTabPane = new TabPane();
        pluginTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        VBox.setVgrow(pluginTabPane, Priority.ALWAYS);

        VBox content = new VBox(pluginTabPane);
        content.setPrefWidth(280);
        content.setMinWidth(200);

        dockNode = new DockNode(content, "Plugins");
        dockNode.setClosable(false);
    }

    /**
     * Call after setting the root on pluginBrowser to wire up the listener
     * that turns each top-level TreeItem into a dedicated tab.
     */
    public void initialize() {
        TreeItem<String> root = pluginBrowser.getRoot();
        if (root == null) {
            return;
        }

        // Create tabs for any nodes already present
        for (TreeItem<String> node : root.getChildren()) {
            createPluginTab(node);
        }

        // Listen for future additions/removals
        root.getChildren().addListener((ListChangeListener<TreeItem<String>>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (TreeItem<String> added : change.getAddedSubList()) {
                        Platform.runLater(() -> createPluginTab(added));
                    }
                }
                if (change.wasRemoved()) {
                    for (TreeItem<String> removed : change.getRemoved()) {
                        Platform.runLater(() -> removePluginTab(removed));
                    }
                }
            }
        });
    }

    private void createPluginTab(TreeItem<String> pluginNode) {
        String name = pluginNode.getValue();

        // Avoid duplicate tabs
        for (Tab tab : pluginTabPane.getTabs()) {
            if (name.equals(tab.getUserData())) {
                pluginTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        // Create a dedicated TreeView for this plugin's subtree
        TreeItem<String> tabRoot = new TreeItem<>(name);
        tabRoot.setExpanded(true);

        // Move children from the bridge node into the tab's own root
        tabRoot.getChildren().addAll(pluginNode.getChildren());

        // Keep in sync: if the plugin adds more children later
        pluginNode.getChildren().addListener((ListChangeListener<TreeItem<String>>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (TreeItem<String> added : change.getAddedSubList()) {
                        Platform.runLater(() -> tabRoot.getChildren().add(added));
                    }
                }
                if (change.wasRemoved()) {
                    for (TreeItem<String> removed : change.getRemoved()) {
                        Platform.runLater(() -> tabRoot.getChildren().remove(removed));
                    }
                }
            }
        });

        TreeView<String> tabTreeView = new TreeView<>(tabRoot);
        tabTreeView.setShowRoot(true);
        VBox.setVgrow(tabTreeView, Priority.ALWAYS);

        Tab tab = new Tab(name);
        tab.setUserData(name);
        tab.setContent(tabTreeView);
        tab.setClosable(true);

        // Pick an icon based on plugin name
        FontIcon tabIcon = new FontIcon(guessIcon(name));
        tabIcon.setIconSize(14);
        tab.setGraphic(tabIcon);

        // When user closes the tab, also remove from the bridge tree
        tab.setOnClosed(event -> {
            TreeItem<String> root = pluginBrowser.getRoot();
            if (root != null) {
                root.getChildren().remove(pluginNode);
            }
        });

        pluginTabPane.getTabs().add(tab);
        pluginTabPane.getSelectionModel().select(tab);
    }

    private void removePluginTab(TreeItem<String> pluginNode) {
        String name = pluginNode.getValue();
        pluginTabPane.getTabs().removeIf(tab -> name.equals(tab.getUserData()));
    }

    private Feather guessIcon(String name) {
        if (name == null) return Feather.BOX;
        String lower = name.toLowerCase();
        if (lower.contains("mongo") || lower.contains("database") || lower.contains("redis")
                || lower.contains("cassandra") || lower.contains("nosql")) {
            return Feather.DATABASE;
        }
        if (lower.contains("visual") || lower.contains("schema") || lower.contains("diagram")) {
            return Feather.EYE;
        }
        if (lower.contains("editor") || lower.contains("script")) {
            return Feather.EDIT;
        }
        return Feather.BOX;
    }

    public void dock(DockPane dockPane, DockPos position, DockNode sibling) {
        dockNode.dock(dockPane, position, sibling);
    }

    public DockNode getDockNode() {
        return dockNode;
    }

    public TreeView<String> getPluginBrowser() {
        return pluginBrowser;
    }

    public TabPane getTabPane() {
        return pluginTabPane;
    }

    public Separator getPluginBrowserSeparator() {
        return pluginBrowserSeparator;
    }

    public HBox getPluginBrowserHeader() {
        return pluginBrowserHeader;
    }
}
