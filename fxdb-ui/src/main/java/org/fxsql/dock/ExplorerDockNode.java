package org.fxsql.dock;

import org.dockfx.DockNode;
import org.dockfx.DockPane;
import org.dockfx.DockPos;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class ExplorerDockNode implements FXDockNode {

    private final DockNode dockNode;
    private final FileExplorerPane fileExplorerPane;
    private final WorkspaceExplorerPane workspaceExplorerPane;

    public ExplorerDockNode() {
        fileExplorerPane = new FileExplorerPane();
        workspaceExplorerPane = new WorkspaceExplorerPane();

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab fileTab = new Tab("Files", fileExplorerPane);
        Tab workspaceTab = new Tab("Workspaces", workspaceExplorerPane);

        tabPane.getTabs().addAll(fileTab, workspaceTab);
        tabPane.setPrefWidth(280);
        tabPane.setMinWidth(200);

        dockNode = new DockNode(tabPane, "Explorer");
    }

    public void dock(DockPane dockPane, DockPos position, DockNode sibling) {
        dockNode.dock(dockPane, position, sibling);
    }

    @Override
    public DockNode getDockNode() {
        return dockNode;
    }

    public FileExplorerPane getFileExplorerPane() {
        return fileExplorerPane;
    }

    public WorkspaceExplorerPane getWorkspaceExplorerPane() {
        return workspaceExplorerPane;
    }
}
