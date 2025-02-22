package org.fxsql.fxdb.components.sqlScriptExecutor;

import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.fxsql.fxdb.databaseManagement.DatabaseConnection;
import org.fxsql.fxdb.services.TableInteractionService;


public class SQLScriptPane extends VBox {

    private SQLEditorToolBar toolBar;
    private SQLEditor editor;

    private TableView<ObservableList<Object>> tableView;

    private DatabaseConnection connection;

    private TableInteractionService tableInteractionService;

    public SQLScriptPane(DatabaseConnection connection) {
//        super(new SQLEditorToolBar(), new SQLEditor());
        super();
        toolBar = new SQLEditorToolBar();
        editor = new SQLEditor();
        assert connection != null;
        tableView = new TableView<>();
        tableInteractionService = new TableInteractionService(tableView);


        this.getChildren().addAll(toolBar, editor);

        Button executeScriptBtn = toolBar.getExecuteScript();
        executeScriptBtn.setOnMouseClicked(this::executeScriptOnBtnAction);
    }

    private void executeScriptOnBtnAction(MouseEvent mouseEvent) {
        String[] queries = editor.sqlQueriesInEditor();
        
    }


}
