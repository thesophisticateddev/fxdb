package org.fxsql.components.sqlScriptExecutor;

import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class SQLEditorToolBar extends ToolBar {

    private Button executeScript;

    private Button stopExecutingScript;

    private Button executeSelection;

    public SQLEditorToolBar(){
        super();
        setExecuteSelection();
        setStopExecutingScript();
        setUpExecuteScriptButton();
        this.getItems().addAll(executeScript,executeSelection,stopExecutingScript);
    }

    private void setUpExecuteScriptButton(){
        this.executeScript = new Button("Run", new FontIcon(Feather.PLAY));
        this.executeScript.setOnMouseClicked(mouseEvent -> {
            System.out.println("Execute script clicked!");
        });

    }

    private void setStopExecutingScript(){
        this.stopExecutingScript = new Button("Stop",new FontIcon(Feather.SQUARE));
        this.stopExecutingScript.setOnMouseClicked(mouseEvent -> {
            System.out.println("Stop script clicked!");
        });

    }


    private void setExecuteSelection(){
        this.executeSelection = new Button("Execute Selection",new FontIcon(Feather.PLAY_CIRCLE));
        this.executeSelection.setOnMouseClicked(mouseEvent -> {
            System.out.println("Stop script clicked!");
        });

    }
}
