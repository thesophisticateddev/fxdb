package org.fxsql.dock;

import atlantafx.base.theme.Styles;
import javafx.scene.control.TabPane;
import org.dockfx.DockNode;
import org.dockfx.DockPane;
import org.dockfx.DockPos;

public class WorkspaceDockNode {

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

    public void dock(DockPane dockPane, DockPos position, DockNode sibling) {
        dockNode.dock(dockPane, position, sibling);
    }

    public DockNode getDockNode() {
        return dockNode;
    }

    public TabPane getTabPane() {
        return tabPane;
    }
}
