package org.fxsql.dock;

import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.fxsql.events.DockEvents;
import org.fxsql.events.EventBus;
import org.fxsql.events.FxdbDockEvent;
import org.fxsql.workspace.Workspace;
import org.fxsql.workspace.WorkspaceService;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class WorkspaceExplorerPane extends VBox {

    private final ListView<Workspace> workspaceList = new ListView<>();
    private final ListView<Path> fileList = new ListView<>();

    public WorkspaceExplorerPane() {
        Button newBtn = new Button("New Workspace");
        FontIcon newIcon = new FontIcon(Feather.PLUS);
        newIcon.setIconSize(14);
        newBtn.setGraphic(newIcon);

        Button deleteBtn = new Button("Delete");
        FontIcon deleteIcon = new FontIcon(Feather.TRASH_2);
        deleteIcon.setIconSize(14);
        deleteBtn.setGraphic(deleteIcon);

        Button addFilesBtn = new Button("Add Files");
        FontIcon addFilesIcon = new FontIcon(Feather.FILE_PLUS);
        addFilesIcon.setIconSize(14);
        addFilesBtn.setGraphic(addFilesIcon);

        Button loadBtn = new Button("Load Workspace");
        FontIcon loadIcon = new FontIcon(Feather.PLAY);
        loadIcon.setIconSize(14);
        loadBtn.setGraphic(loadIcon);

        workspaceList.setItems(WorkspaceService.loadAll());

        // Show workspace name in list cells
        workspaceList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Workspace item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName());
                    FontIcon icon = new FontIcon(Feather.BRIEFCASE);
                    icon.setIconSize(14);
                    setGraphic(icon);
                }
            }
        });

        // Show file name in file list cells
        fileList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getFileName().toString());
                    setTooltip(new Tooltip(item.toString()));
                    FontIcon icon = new FontIcon(Feather.FILE_TEXT);
                    icon.setIconSize(14);
                    setGraphic(icon);
                }
            }
        });

        // When a workspace is selected, show its files
        workspaceList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> {
                    if (selected != null) {
                        fileList.getItems().setAll(selected.getFiles());
                    } else {
                        fileList.getItems().clear();
                    }
                }
        );

        newBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("New Workspace");
            dialog.setHeaderText("Enter workspace name:");
            dialog.showAndWait().ifPresent(name -> {
                if (!name.isBlank()) {
                    Workspace ws = WorkspaceService.create(name.trim());
                    workspaceList.getItems().add(ws);
                }
            });
        });

        deleteBtn.setOnAction(e -> {
            Workspace selected = workspaceList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                WorkspaceService.delete(selected);
                workspaceList.getItems().remove(selected);
                fileList.getItems().clear();
            }
        });

        loadBtn.setOnAction(e -> {
            Workspace selected = workspaceList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                EventBus.fireEvent(new FxdbDockEvent<>(DockEvents.WORKSPACE_LOADED, selected));
            }
        });

        addFilesBtn.setOnAction(e -> {
            Workspace selected = workspaceList.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Add SQL Files to Workspace");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("SQL Files", "*.sql"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            List<File> files = fileChooser.showOpenMultipleDialog(this.getScene().getWindow());
            if (files != null) {
                for (File f : files) {
                    Path p = f.toPath();
                    if (!selected.getFiles().contains(p)) {
                        selected.getFiles().add(p);
                    }
                }
                WorkspaceService.save(selected);
                fileList.getItems().setAll(selected.getFiles());
            }
        });

        // Right-click on file list to remove a file from the workspace
        MenuItem removeFileItem = new MenuItem("Remove from Workspace");
        removeFileItem.setGraphic(new FontIcon(Feather.X));
        removeFileItem.setOnAction(e -> {
            Workspace selected = workspaceList.getSelectionModel().getSelectedItem();
            Path selectedFile = fileList.getSelectionModel().getSelectedItem();
            if (selected != null && selectedFile != null) {
                selected.getFiles().remove(selectedFile);
                WorkspaceService.save(selected);
                fileList.getItems().setAll(selected.getFiles());
            }
        });
        fileList.setContextMenu(new ContextMenu(removeFileItem));

        ToolBar toolbar = new ToolBar(newBtn, deleteBtn, addFilesBtn);
        toolbar.getStyleClass().add("explorer-toolbar");

        Label filesLabel = new Label("Files:");
        filesLabel.getStyleClass().add("explorer-section-label");

        VBox.setVgrow(workspaceList, Priority.ALWAYS);
        VBox.setVgrow(fileList, Priority.ALWAYS);

        this.getChildren().addAll(toolbar, workspaceList, new Separator(), filesLabel, fileList, loadBtn);
        this.setSpacing(4);
        this.getStyleClass().add("workspace-explorer-pane");
    }

    public void refreshWorkspaces() {
        workspaceList.setItems(WorkspaceService.loadAll());
    }
}
