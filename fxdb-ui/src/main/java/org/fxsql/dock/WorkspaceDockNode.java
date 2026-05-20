package org.fxsql.dock;

import org.dockfx.DockNode;
import org.dockfx.DockPane;
import org.dockfx.DockPos;

import atlantafx.base.theme.Styles;
import javafx.scene.control.TabPane;

public class WorkspaceDockNode implements FXDockNode {

    private final DockNode dockNode;
    private final TabPane tabPane;

    public WorkspaceDockNode() {
        tabPane = new TabPane();
        tabPane.getStyleClass().add(Styles.TABS_FLOATING);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        tabPane.setMinWidth(450);

        dockNode = new DockNode(tabPane, "Workspace");
        dockNode.setClosable(false);
    }

    @Override
    public void dock(DockPane dockPane, DockPos position, DockNode sibling) {
        dockNode.dock(dockPane, position, sibling);
    }

    @Override
    public DockNode getDockNode() {
        return dockNode;
    }

    public TabPane getTabPane() {
        return tabPane;
    }
}
