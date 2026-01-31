package org.fxsql.components.sqlScriptExecutor;

import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Toolbar for the SQL Editor with execution controls.
 */
public class SQLEditorToolBar extends ToolBar {

    private Button executeScript;
    private Button stopExecutingScript;
    private Button executeSelection;
    private Button formatSql;
    private Button clearEditor;

    public SQLEditorToolBar() {
        super();
        initializeButtons();
        this.getItems().addAll(
                executeScript,
                executeSelection,
                new Separator(),
                stopExecutingScript,
                new Separator(),
                formatSql,
                clearEditor
        );
    }

    private void initializeButtons() {
        // Run all queries
        executeScript = createButton("Run All", Feather.PLAY, "Execute all queries (Ctrl+Enter)");
        executeScript.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        // Run selected query
        executeSelection = createButton("Run Selection", Feather.PLAY_CIRCLE, "Execute selected text (Ctrl+Shift+Enter)");

        // Stop execution
        stopExecutingScript = createButton("Stop", Feather.SQUARE, "Stop query execution");
        stopExecutingScript.setDisable(true); // Disabled by default

        // Format SQL
        formatSql = createButton("Format", Feather.ALIGN_LEFT, "Format SQL (Ctrl+Shift+F)");

        // Clear editor
        clearEditor = createButton("Clear", Feather.TRASH_2, "Clear editor");
    }

    private Button createButton(String text, Feather icon, String tooltipText) {
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(14);

        Button button = new Button(text, fontIcon);
        button.setTooltip(new Tooltip(tooltipText));

        return button;
    }

    public Button getExecuteScript() {
        return executeScript;
    }

    public Button getExecuteSelection() {
        return executeSelection;
    }

    public Button getStopExecutingScript() {
        return stopExecutingScript;
    }

    public Button getFormatSql() {
        return formatSql;
    }

    public Button getClearEditor() {
        return clearEditor;
    }

    /**
     * Sets the running state of the toolbar.
     * Disables/enables buttons appropriately during query execution.
     */
    public void setRunning(boolean running) {
        executeScript.setDisable(running);
        executeSelection.setDisable(running);
        stopExecutingScript.setDisable(!running);
    }
}