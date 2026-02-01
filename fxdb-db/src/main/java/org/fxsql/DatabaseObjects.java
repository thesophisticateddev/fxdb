package org.fxsql;

import java.util.Collections;
import java.util.List;

/**
 * Holds all database objects organized by type.
 * This class is used to efficiently load and display database structure.
 */
public class DatabaseObjects {

    private final List<String> tables;
    private final List<String> views;
    private final List<String> triggers;
    private final List<String> functions;
    private final List<String> indexes;

    public DatabaseObjects(List<String> tables, List<String> views,
                           List<String> triggers, List<String> functions,
                           List<String> indexes) {
        this.tables = tables != null ? tables : Collections.emptyList();
        this.views = views != null ? views : Collections.emptyList();
        this.triggers = triggers != null ? triggers : Collections.emptyList();
        this.functions = functions != null ? functions : Collections.emptyList();
        this.indexes = indexes != null ? indexes : Collections.emptyList();
    }

    public List<String> getTables() {
        return tables;
    }

    public List<String> getViews() {
        return views;
    }

    public List<String> getTriggers() {
        return triggers;
    }

    public List<String> getFunctions() {
        return functions;
    }

    public List<String> getIndexes() {
        return indexes;
    }

    public boolean isEmpty() {
        return tables.isEmpty() && views.isEmpty() && triggers.isEmpty()
                && functions.isEmpty() && indexes.isEmpty();
    }

    public int getTotalCount() {
        return tables.size() + views.size() + triggers.size()
                + functions.size() + indexes.size();
    }
}