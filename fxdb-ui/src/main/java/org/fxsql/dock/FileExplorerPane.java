package org.fxsql.dock;

import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.fxsql.events.DockEvents;
import org.fxsql.events.EventBus;
import org.fxsql.events.FxdbDockEvent;
import org.fxsql.workspace.Workspace;
import org.fxsql.workspace.WorkspaceService;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FileExplorerPane extends VBox {

    private final TreeView<Path> treeView = new TreeView<>();
    private Path rootPath;

    public FileExplorerPane() {
        Button openFolderBtn = new Button("Open Folder");
        FontIcon folderIcon = new FontIcon(Feather.FOLDER);
        folderIcon.setIconSize(14);
        openFolderBtn.setGraphic(folderIcon);

        Button refreshBtn = new Button("Refresh");
        FontIcon refreshIcon = new FontIcon(Feather.REFRESH_CW);
        refreshIcon.setIconSize(14);
        refreshBtn.setGraphic(refreshIcon);

        openFolderBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select SQL Folder");
            File chosen = chooser.showDialog(this.getScene().getWindow());
            if (chosen != null) {
                rootPath = chosen.toPath();
                populateTree(rootPath);
            }
        });

        refreshBtn.setOnAction(e -> {
            if (rootPath != null) populateTree(rootPath);
        });

        // Double-click to open a .sql file
        treeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<Path> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null && Files.isRegularFile(selected.getValue())) {
                    EventBus.fireEvent(new FxdbDockEvent<>(DockEvents.SQL_FILE_OPENED, selected.getValue()));
                }
            }
        });

        // Show only file/folder name, not full path
        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getFileName().toString());
                    FontIcon icon;
                    if (Files.isDirectory(item)) {
                        icon = new FontIcon(Feather.FOLDER);
                    } else {
                        icon = new FontIcon(Feather.FILE_TEXT);
                    }
                    icon.setIconSize(14);
                    setGraphic(icon);
                }
            }
        });

        ToolBar toolbar = new ToolBar(openFolderBtn, refreshBtn);
        toolbar.getStyleClass().add("explorer-toolbar");
        VBox.setVgrow(treeView, Priority.ALWAYS);
        this.getChildren().addAll(toolbar, treeView);
        this.getStyleClass().add("file-explorer-pane");
    }

    /**
     * Sets up the right-click context menu for adding files to workspaces.
     * Called after workspaces are available.
     */
    public void setupContextMenu() {
        treeView.setContextMenu(buildContextMenu());
    }

    private ContextMenu buildContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.setOnShowing(e -> {
            contextMenu.getItems().clear();
            TreeItem<Path> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected == null || !Files.isRegularFile(selected.getValue())) {
                contextMenu.hide();
                return;
            }

            Path selectedPath = selected.getValue();

            // "Open" item
            MenuItem openItem = new MenuItem("Open");
            openItem.setOnAction(ev -> EventBus.fireEvent(
                    new FxdbDockEvent<>(DockEvents.SQL_FILE_OPENED, selectedPath)));
            contextMenu.getItems().add(openItem);

            // "Add to Workspace" submenu
            Menu addToWorkspace = new Menu("Add to Workspace");
            for (Workspace ws : WorkspaceService.loadAll()) {
                MenuItem wsItem = new MenuItem(ws.getName());
                wsItem.setOnAction(ev -> {
                    if (!ws.getFiles().contains(selectedPath)) {
                        ws.getFiles().add(selectedPath);
                        WorkspaceService.save(ws);
                    }
                });
                addToWorkspace.getItems().add(wsItem);
            }
            if (addToWorkspace.getItems().isEmpty()) {
                MenuItem noWs = new MenuItem("(no workspaces)");
                noWs.setDisable(true);
                addToWorkspace.getItems().add(noWs);
            }
            contextMenu.getItems().add(addToWorkspace);
        });
        return contextMenu;
    }

    private void populateTree(Path root) {
        TreeItem<Path> rootItem = buildTreeItem(root);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
    }

    private TreeItem<Path> buildTreeItem(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.list(path)) {
                stream.filter(p -> Files.isDirectory(p) || p.toString().endsWith(".sql"))
                      .sorted((a, b) -> {
                          // Directories first, then alphabetical
                          boolean aDir = Files.isDirectory(a);
                          boolean bDir = Files.isDirectory(b);
                          if (aDir != bDir) return aDir ? -1 : 1;
                          return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                      })
                      .map(this::buildTreeItem)
                      .forEach(item.getChildren()::add);
            } catch (IOException ignored) {}
        }
        return item;
    }
}
