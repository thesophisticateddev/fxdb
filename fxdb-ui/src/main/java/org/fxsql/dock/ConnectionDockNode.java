package org.fxsql.dock;

import atlantafx.base.controls.Tile;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.dockfx.DockNode;
import org.dockfx.DockPane;
import org.dockfx.DockPos;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayDeque;
import java.util.Deque;

public class ConnectionDockNode {

    private final DockNode dockNode;
    private final TreeView<String> tableBrowser;
    private final Tile databaseSelectorTile;
    private final TreeView<String> pluginBrowser;
    private final Separator pluginBrowserSeparator;
    private final HBox pluginBrowserHeader;
    private final Button refreshButton;

    public ConnectionDockNode() {
        // Database selector tile
        databaseSelectorTile = new Tile();

        String btnBase = "-fx-background-color: transparent; -fx-padding: 1 2; -fx-border-color: transparent; -fx-border-width: 1;";
        String btnHover = "-fx-background-color: transparent; -fx-padding: 1 2; -fx-border-color: #c0c0c0; -fx-border-width: 1;";

        // Refresh button (icon-only)
        refreshButton = new Button();
        FontIcon refreshIcon = new FontIcon(Feather.REFRESH_CW);
        refreshIcon.setIconSize(14);
        refreshButton.setGraphic(refreshIcon);
        refreshButton.setTooltip(new Tooltip("Refresh"));
        refreshButton.setStyle(btnBase);
        refreshButton.setOnMouseEntered(e -> refreshButton.setStyle(btnHover));
        refreshButton.setOnMouseExited(e -> refreshButton.setStyle(btnBase));

        // Expand All button
        Button expandAllBtn = new Button();
        FontIcon expandIcon = new FontIcon(Feather.MAXIMIZE_2);
        expandIcon.setIconSize(14);
        expandAllBtn.setGraphic(expandIcon);
        expandAllBtn.setTooltip(new Tooltip("Expand All"));
        expandAllBtn.setStyle(btnBase);
        expandAllBtn.setOnMouseEntered(e -> expandAllBtn.setStyle(btnHover));
        expandAllBtn.setOnMouseExited(e -> expandAllBtn.setStyle(btnBase));
        expandAllBtn.setOnAction(e -> setAllExpanded(true));

        // Collapse All button
        Button collapseAllBtn = new Button();
        FontIcon collapseIcon = new FontIcon(Feather.MINIMIZE_2);
        collapseIcon.setIconSize(14);
        collapseAllBtn.setGraphic(collapseIcon);
        collapseAllBtn.setTooltip(new Tooltip("Collapse All"));
        collapseAllBtn.setStyle(btnBase);
        collapseAllBtn.setOnMouseEntered(e -> collapseAllBtn.setStyle(btnHover));
        collapseAllBtn.setOnMouseExited(e -> collapseAllBtn.setStyle(btnBase));
        collapseAllBtn.setOnAction(e -> setAllExpanded(false));

        Region ribbonSpacer = new Region();
        HBox.setHgrow(ribbonSpacer, Priority.ALWAYS);

        HBox treeRibbon = new HBox(4, expandAllBtn, collapseAllBtn, ribbonSpacer, refreshButton);
        treeRibbon.setPadding(new Insets(1, 2, 1, 2));
        treeRibbon.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Table browser tree
        tableBrowser = new TreeView<>();
        VBox.setVgrow(tableBrowser, Priority.ALWAYS);

        // Plugin browser (hidden by default)
        pluginBrowserSeparator = new Separator();
        pluginBrowserSeparator.setVisible(false);
        pluginBrowserSeparator.setManaged(false);

        Label pluginLabel = new Label("Plugin Browser");
        pluginLabel.setStyle("-fx-font-weight: bold;");

        pluginBrowserHeader = new HBox(5, pluginLabel);
        pluginBrowserHeader.setPadding(new Insets(5));
        pluginBrowserHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        pluginBrowserHeader.setVisible(false);
        pluginBrowserHeader.setManaged(false);

        pluginBrowser = new TreeView<>();
        pluginBrowser.setVisible(false);
        pluginBrowser.setManaged(false);
        VBox.setVgrow(pluginBrowser, Priority.ALWAYS);

        // Assemble the panel
        VBox content = new VBox();
        content.getChildren().addAll(
            databaseSelectorTile,
            new Separator(),
            treeRibbon,
            tableBrowser,
            pluginBrowserSeparator,
            pluginBrowserHeader,
            pluginBrowser
        );
        content.setPrefWidth(280);
        content.setMinWidth(200);

        dockNode = new DockNode(content, "Connections");
        dockNode.setClosable(false);
    }

    public void dock(DockPane dockPane) {
        dockNode.dock(dockPane, DockPos.LEFT);
    }

    public DockNode getDockNode() {
        return dockNode;
    }

    public TreeView<String> getTableBrowser() {
        return tableBrowser;
    }

    public Tile getDatabaseSelectorTile() {
        return databaseSelectorTile;
    }

    public TreeView<String> getPluginBrowser() {
        return pluginBrowser;
    }

    public Separator getPluginBrowserSeparator() {
        return pluginBrowserSeparator;
    }

    public HBox getPluginBrowserHeader() {
        return pluginBrowserHeader;
    }

    public Button getRefreshButton() {
        return refreshButton;
    }

    /**
     * Iterative BFS expand/collapse of all tree nodes. O(n) time, no recursion.
     */
    private void setAllExpanded(boolean expanded) {
        TreeItem<String> root = tableBrowser.getRoot();
        if (root == null) {
            return;
        }
        Deque<TreeItem<String>> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TreeItem<String> node = stack.pop();
            if (!node.isLeaf()) {
                node.setExpanded(expanded);
                for (TreeItem<String> child : node.getChildren()) {
                    stack.push(child);
                }
            }
        }
    }
}
