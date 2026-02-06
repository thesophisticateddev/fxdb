package org.fxsql.components;

import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import org.fxsql.DatabaseConnection;
import org.fxsql.components.sqlScriptExecutor.SQLScriptPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Context menu for table items in the database browser tree.
 * Provides options to view table data, open SQL scripts, and view table info.
 */
public class TableContextMenu extends ContextMenu {

    private final TreeView<String> tableSelector;
    private final MenuItem openItem;
    private final MenuItem openScriptWindow;
    private final MenuItem tableInfoItem;

    private DatabaseConnection databaseConnection;
    private TabPane tabPane;

    public TableContextMenu(DatabaseConnection connection, TreeView<String> tableSelector) {
        super();
        this.tableSelector = tableSelector;
        this.databaseConnection = connection;

        // Create menu items with icons
        openItem = new MenuItem("Show Table Data");
        FontIcon openIcon = new FontIcon(Feather.FILE);
        openIcon.setIconSize(14);
        openItem.setGraphic(openIcon);

        openScriptWindow = new MenuItem("Open SQL Script");
        FontIcon scriptIcon = new FontIcon(Feather.FILE_TEXT);
        scriptIcon.setIconSize(14);
        openScriptWindow.setGraphic(scriptIcon);

        tableInfoItem = new MenuItem("Table Info");
        FontIcon infoIcon = new FontIcon(Feather.INFO);
        infoIcon.setIconSize(14);
        tableInfoItem.setGraphic(infoIcon);
    }

    public void setDatabaseConnection(DatabaseConnection connection) {
        this.databaseConnection = connection;
    }

    public void setTabPane(TabPane tabPane) {
        this.tabPane = tabPane;
    }

    private void handleOpenItem() {
        TreeItem<String> selectedItem = tableSelector.getSelectionModel().getSelectedItem();
        if (selectedItem != null && tabPane != null && databaseConnection != null) {
            String tableName = selectedItem.getValue();

            // Check if a tab for this table already exists
            for (Tab tab : tabPane.getTabs()) {
                if (tab.getContent() instanceof EditableTablePane existingPane) {
                    if (tableName.equals(existingPane.getCurrentTableName())) {
                        // Table tab exists - select it and refresh with page reset
                        tabPane.getSelectionModel().select(tab);
                        existingPane.refreshAndResetPage();
                        return;
                    }
                }
            }

            // Create a new tab for this table
            Tab tableTab = new Tab(tableName);
            FontIcon tabIcon = new FontIcon(Feather.GRID);
            tabIcon.setIconSize(12);
            tableTab.setGraphic(tabIcon);

            EditableTablePane newTablePane = new EditableTablePane();
            newTablePane.loadTableData(databaseConnection, tableName);
            tableTab.setContent(newTablePane);

            tableTab.setOnClosed(event -> {
                newTablePane.shutdown();
            });

            tabPane.getTabs().add(tableTab);
            tabPane.getSelectionModel().select(tableTab);
        }
    }

    private void handleOpenScriptWindowInTab() {
        if (tabPane != null && databaseConnection != null) {
            Tab tab = new Tab("Untitled");
            FontIcon scriptTabIcon = new FontIcon(Feather.FILE_TEXT);
            scriptTabIcon.setIconSize(12);
            tab.setGraphic(scriptTabIcon);

            SQLScriptPane pane = new SQLScriptPane(databaseConnection);

            // Set callback to update tab title when file changes
            pane.setTitleChangeCallback(title -> tab.setText(title));

            tab.setContent(pane);
            tab.setOnCloseRequest(event -> {
                if (pane.isModified()) {
                    // Confirm before closing if there are unsaved changes
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Unsaved Changes");
                    alert.setHeaderText("You have unsaved changes");
                    alert.setContentText("Do you want to save before closing?");

                    ButtonType saveButton = new ButtonType("Save");
                    ButtonType discardButton = new ButtonType("Discard");
                    ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(saveButton, discardButton, cancelButton);

                    java.util.Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent()) {
                        if (result.get() == saveButton) {
                            pane.saveFile();
                            if (pane.isModified()) {
                                // Save was cancelled
                                event.consume();
                                return;
                            }
                        } else if (result.get() == cancelButton) {
                            event.consume();
                            return;
                        }
                    } else {
                        event.consume();
                        return;
                    }
                }
            });
            tab.setOnClosed(event -> {
                pane.shutdown();
            });
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
        }
    }

    private void handleTableInfo() {
        TreeItem<String> selectedItem = tableSelector.getSelectionModel().getSelectedItem();
        if (selectedItem != null && tabPane != null && databaseConnection != null) {
            String tableName = selectedItem.getValue();

            // Check if a table info tab already exists for this table
            for (Tab tab : tabPane.getTabs()) {
                if (tab.getContent() instanceof TableInfoPane infoPane) {
                    if (tableName.equals(infoPane.getCurrentTableName())) {
                        tabPane.getSelectionModel().select(tab);
                        return;
                    }
                }
            }

            // Create new table info tab
            Tab tab = new Tab("Info: " + tableName);
            FontIcon tabIcon = new FontIcon(Feather.INFO);
            tabIcon.setIconSize(12);
            tab.setGraphic(tabIcon);

            TableInfoPane infoPane = new TableInfoPane();
            infoPane.loadTableInfo(databaseConnection, tableName);
            tab.setContent(infoPane);

            tab.setOnClosed(event -> {
                infoPane.shutdown();
            });

            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
        }
    }

    public void showContextMenu(DatabaseConnection connection, MouseEvent mouseEvent) {
        TreeItem<String> selectedItem = tableSelector.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            this.databaseConnection = connection;
            this.getItems().clear();

            this.getItems().addAll(openItem, tableInfoItem, new SeparatorMenuItem(), openScriptWindow);
            openItem.setOnAction(event -> handleOpenItem());
            tableInfoItem.setOnAction(event -> handleTableInfo());
            openScriptWindow.setOnAction(event -> handleOpenScriptWindowInTab());

            this.show(tableSelector, mouseEvent.getScreenX(), mouseEvent.getScreenY());
        }
    }
}