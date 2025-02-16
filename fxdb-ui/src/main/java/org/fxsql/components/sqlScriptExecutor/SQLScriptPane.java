package org.fxsql.components.sqlScriptExecutor;

import javafx.scene.layout.VBox;

public class SQLScriptPane extends VBox {

    public SQLScriptPane() {
        super(new SQLEditorToolBar(), new SQLEditor());
    }
}
