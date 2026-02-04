package org.fxsql.workspace;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the state of a workspace for a specific database connection.
 * This allows saving and restoring user work when switching between connections.
 */
public class WorkspaceState implements Serializable {
    private static final long serialVersionUID = 1L;

    private String connectionName;
    private LocalDateTime savedAt;

    // Table data state
    private String currentTableName;
    private List<Map<String, Object>> pendingRowChanges;
    private List<Map<String, Object>> newRows;
    private List<String> deletedRowKeys;

    // SQL Editor state
    private List<SqlTabState> sqlTabs;
    private int activeTabIndex;

    // Tree expansion state
    private List<String> expandedNodes;

    public WorkspaceState() {
        this.pendingRowChanges = new ArrayList<>();
        this.newRows = new ArrayList<>();
        this.deletedRowKeys = new ArrayList<>();
        this.sqlTabs = new ArrayList<>();
        this.expandedNodes = new ArrayList<>();
        this.savedAt = LocalDateTime.now();
    }

    public WorkspaceState(String connectionName) {
        this();
        this.connectionName = connectionName;
    }

    /**
     * Represents the state of a SQL editor tab.
     */
    public static class SqlTabState implements Serializable {
        private static final long serialVersionUID = 1L;

        private String tabTitle;
        private String sqlContent;
        private boolean modified;
        private int cursorPosition;

        public SqlTabState() {}

        public SqlTabState(String tabTitle, String sqlContent, boolean modified) {
            this.tabTitle = tabTitle;
            this.sqlContent = sqlContent;
            this.modified = modified;
        }

        // Getters and setters
        public String getTabTitle() { return tabTitle; }
        public void setTabTitle(String tabTitle) { this.tabTitle = tabTitle; }

        public String getSqlContent() { return sqlContent; }
        public void setSqlContent(String sqlContent) { this.sqlContent = sqlContent; }

        public boolean isModified() { return modified; }
        public void setModified(boolean modified) { this.modified = modified; }

        public int getCursorPosition() { return cursorPosition; }
        public void setCursorPosition(int cursorPosition) { this.cursorPosition = cursorPosition; }
    }

    // Getters and setters
    public String getConnectionName() { return connectionName; }
    public void setConnectionName(String connectionName) { this.connectionName = connectionName; }

    public LocalDateTime getSavedAt() { return savedAt; }
    public void setSavedAt(LocalDateTime savedAt) { this.savedAt = savedAt; }

    public String getCurrentTableName() { return currentTableName; }
    public void setCurrentTableName(String currentTableName) { this.currentTableName = currentTableName; }

    public List<Map<String, Object>> getPendingRowChanges() { return pendingRowChanges; }
    public void setPendingRowChanges(List<Map<String, Object>> pendingRowChanges) {
        this.pendingRowChanges = pendingRowChanges;
    }

    public List<Map<String, Object>> getNewRows() { return newRows; }
    public void setNewRows(List<Map<String, Object>> newRows) { this.newRows = newRows; }

    public List<String> getDeletedRowKeys() { return deletedRowKeys; }
    public void setDeletedRowKeys(List<String> deletedRowKeys) { this.deletedRowKeys = deletedRowKeys; }

    public List<SqlTabState> getSqlTabs() { return sqlTabs; }
    public void setSqlTabs(List<SqlTabState> sqlTabs) { this.sqlTabs = sqlTabs; }

    public int getActiveTabIndex() { return activeTabIndex; }
    public void setActiveTabIndex(int activeTabIndex) { this.activeTabIndex = activeTabIndex; }

    public List<String> getExpandedNodes() { return expandedNodes; }
    public void setExpandedNodes(List<String> expandedNodes) { this.expandedNodes = expandedNodes; }

    /**
     * Returns true if this workspace has any unsaved changes.
     */
    public boolean hasUnsavedChanges() {
        if (!pendingRowChanges.isEmpty() || !newRows.isEmpty() || !deletedRowKeys.isEmpty()) {
            return true;
        }
        for (SqlTabState tab : sqlTabs) {
            if (tab.isModified()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a summary of unsaved changes.
     */
    public String getChangesSummary() {
        StringBuilder sb = new StringBuilder();

        int rowChanges = pendingRowChanges.size() + newRows.size() + deletedRowKeys.size();
        if (rowChanges > 0) {
            sb.append("- ").append(rowChanges).append(" table row change(s)\n");
        }

        long modifiedTabs = sqlTabs.stream().filter(SqlTabState::isModified).count();
        if (modifiedTabs > 0) {
            sb.append("- ").append(modifiedTabs).append(" unsaved SQL tab(s)\n");
        }

        return sb.toString();
    }
}
