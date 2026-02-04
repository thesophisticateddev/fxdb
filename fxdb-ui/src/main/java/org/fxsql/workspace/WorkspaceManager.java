package org.fxsql.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages workspace states for different database connections.
 * Saves and restores user work (table changes, SQL tabs) when switching connections.
 */
@Singleton
public class WorkspaceManager {

    private static final Logger logger = Logger.getLogger(WorkspaceManager.class.getName());
    private static final String WORKSPACE_DIRECTORY = "META-DATA/workspaces";

    private final Map<String, WorkspaceState> workspaceCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    private String currentConnectionName;

    public WorkspaceManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        ensureDirectoryExists();
        loadAllWorkspaces();
    }

    private void ensureDirectoryExists() {
        File dir = new File(WORKSPACE_DIRECTORY);
        if (!dir.exists()) {
            dir.mkdirs();
            logger.info("Created workspace directory: " + dir.getAbsolutePath());
        }
    }

    /**
     * Loads all saved workspace states from disk.
     */
    private void loadAllWorkspaces() {
        File dir = new File(WORKSPACE_DIRECTORY);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".workspace.json"));

        if (files != null) {
            for (File file : files) {
                try {
                    WorkspaceState state = objectMapper.readValue(file, WorkspaceState.class);
                    if (state.getConnectionName() != null) {
                        workspaceCache.put(state.getConnectionName(), state);
                        logger.fine("Loaded workspace for: " + state.getConnectionName());
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Failed to load workspace: " + file.getName(), e);
                }
            }
        }

        logger.info("Loaded " + workspaceCache.size() + " workspace(s)");
    }

    /**
     * Saves a workspace state for a connection.
     */
    public void saveWorkspace(WorkspaceState state) {
        if (state == null || state.getConnectionName() == null) {
            return;
        }

        state.setSavedAt(LocalDateTime.now());
        workspaceCache.put(state.getConnectionName(), state);

        // Save to disk
        File file = getWorkspaceFile(state.getConnectionName());
        try {
            objectMapper.writeValue(file, state);
            logger.info("Saved workspace for: " + state.getConnectionName());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save workspace: " + state.getConnectionName(), e);
        }
    }

    /**
     * Retrieves a workspace state for a connection.
     */
    public WorkspaceState getWorkspace(String connectionName) {
        WorkspaceState cached = workspaceCache.get(connectionName);
        if (cached != null) {
            return cached;
        }

        // Try to load from disk
        File file = getWorkspaceFile(connectionName);
        if (file.exists()) {
            try {
                WorkspaceState state = objectMapper.readValue(file, WorkspaceState.class);
                workspaceCache.put(connectionName, state);
                return state;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load workspace: " + connectionName, e);
            }
        }

        return null;
    }

    /**
     * Creates a new workspace state for a connection.
     */
    public WorkspaceState createWorkspace(String connectionName) {
        WorkspaceState state = new WorkspaceState(connectionName);
        workspaceCache.put(connectionName, state);
        return state;
    }

    /**
     * Deletes a workspace state.
     */
    public void deleteWorkspace(String connectionName) {
        workspaceCache.remove(connectionName);

        File file = getWorkspaceFile(connectionName);
        if (file.exists()) {
            if (file.delete()) {
                logger.info("Deleted workspace for: " + connectionName);
            }
        }
    }

    /**
     * Clears a workspace state (removes unsaved changes).
     */
    public void clearWorkspace(String connectionName) {
        WorkspaceState state = workspaceCache.get(connectionName);
        if (state != null) {
            state.getPendingRowChanges().clear();
            state.getNewRows().clear();
            state.getDeletedRowKeys().clear();
            state.getSqlTabs().forEach(tab -> tab.setModified(false));
            saveWorkspace(state);
        }
    }

    /**
     * Checks if a connection has unsaved changes.
     */
    public boolean hasUnsavedChanges(String connectionName) {
        WorkspaceState state = workspaceCache.get(connectionName);
        return state != null && state.hasUnsavedChanges();
    }

    /**
     * Gets the summary of unsaved changes for a connection.
     */
    public String getChangesSummary(String connectionName) {
        WorkspaceState state = workspaceCache.get(connectionName);
        return state != null ? state.getChangesSummary() : "";
    }

    /**
     * Gets the current connection name.
     */
    public String getCurrentConnectionName() {
        return currentConnectionName;
    }

    /**
     * Sets the current connection name.
     */
    public void setCurrentConnectionName(String connectionName) {
        this.currentConnectionName = connectionName;
    }

    /**
     * Gets the workspace file for a connection.
     */
    private File getWorkspaceFile(String connectionName) {
        String safeFileName = connectionName.replaceAll("[^a-zA-Z0-9.-]", "_");
        return new File(WORKSPACE_DIRECTORY, safeFileName + ".workspace.json");
    }

    /**
     * Returns all cached workspaces.
     */
    public Map<String, WorkspaceState> getAllWorkspaces() {
        return new ConcurrentHashMap<>(workspaceCache);
    }
}
