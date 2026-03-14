package org.fxsql.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.fxsql.config.AppPaths;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class WorkspaceService {

    private static final Logger logger = Logger.getLogger(WorkspaceService.class.getName());
    private static final Path WORKSPACE_DIR =
            AppPaths.getAppDataDir().resolve("workspaces");
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    static {
        try {
            Files.createDirectories(WORKSPACE_DIR);
        } catch (IOException e) {
            logger.severe("Failed to create workspaces directory: " + e.getMessage());
        }
    }

    public static ObservableList<Workspace> loadAll() {
        List<Workspace> workspaces = new ArrayList<>();
        if (!Files.isDirectory(WORKSPACE_DIR)) {
            return FXCollections.observableArrayList(workspaces);
        }
        try (Stream<Path> paths = Files.list(WORKSPACE_DIR)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                 .sorted()
                 .forEach(p -> {
                     try {
                         WorkspaceJson json = mapper.readValue(p.toFile(), WorkspaceJson.class);
                         Workspace ws = new Workspace(json.name);
                         if (json.files != null) {
                             json.files.stream()
                                       .map(Path::of)
                                       .forEach(ws.getFiles()::add);
                         }
                         workspaces.add(ws);
                     } catch (IOException e) {
                         logger.warning("Failed to load workspace: " + p.getFileName() + " — " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            logger.severe("Failed to list workspaces directory: " + e.getMessage());
        }
        return FXCollections.observableArrayList(workspaces);
    }

    public static Workspace create(String name) {
        Workspace ws = new Workspace(name);
        save(ws);
        return ws;
    }

    public static void save(Workspace workspace) {
        Path file = WORKSPACE_DIR.resolve(sanitizeFileName(workspace.getName()) + ".json");
        WorkspaceJson json = new WorkspaceJson();
        json.name = workspace.getName();
        json.files = workspace.getFiles().stream()
                .map(Path::toString)
                .toList();
        try {
            mapper.writeValue(file.toFile(), json);
        } catch (IOException e) {
            logger.severe("Failed to save workspace: " + workspace.getName() + " — " + e.getMessage());
        }
    }

    public static void delete(Workspace workspace) {
        Path file = WORKSPACE_DIR.resolve(sanitizeFileName(workspace.getName()) + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.severe("Failed to delete workspace: " + workspace.getName() + " — " + e.getMessage());
        }
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // Internal JSON structure
    private static class WorkspaceJson {
        public String name;
        public List<String> files;
    }
}
