package org.fxsql.dock;

import org.dockfx.DockNode;
import org.dockfx.DockPane;
import org.dockfx.DockPos;

public interface FXDockNode {
    DockNode getDockNode();
    default void dock(DockPane dockPane, DockPos position, DockNode sibling) {
        getDockNode().dock(dockPane, position, sibling);
    }
}
