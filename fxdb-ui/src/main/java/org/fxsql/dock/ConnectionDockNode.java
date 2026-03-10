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

        // Refresh button and header
        refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-font-size: 11px;");

        Label browserLabel = new Label("Database Browser");
        browserLabel.setStyle("-fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox browserHeader = new HBox(5, browserLabel, spacer, refreshButton);
        browserHeader.setPadding(new Insets(5));
        browserHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

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
            browserHeader,
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
}
