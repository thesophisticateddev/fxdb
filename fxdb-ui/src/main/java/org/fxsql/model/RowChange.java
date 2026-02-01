package org.fxsql.model;

import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a change to a row in a database table.
 */
public class RowChange {

    public enum ChangeType {
        INSERT,
        UPDATE,
        DELETE
    }

    private final ChangeType type;
    private final String tableName;
    private final ObservableList<Object> originalRow;
    private final ObservableList<Object> currentRow;
    private final Map<Integer, Object> changedColumns; // column index -> new value
    private final java.util.List<String> columnNames;
    private final int primaryKeyIndex;

    public RowChange(ChangeType type, String tableName, ObservableList<Object> originalRow,
                     ObservableList<Object> currentRow, java.util.List<String> columnNames, int primaryKeyIndex) {
        this.type = type;
        this.tableName = tableName;
        this.originalRow = originalRow;
        this.currentRow = currentRow;
        this.columnNames = columnNames;
        this.primaryKeyIndex = primaryKeyIndex;
        this.changedColumns = new HashMap<>();
    }

    public void addColumnChange(int columnIndex, Object newValue) {
        changedColumns.put(columnIndex, newValue);
    }

    public ChangeType getType() {
        return type;
    }

    public String getTableName() {
        return tableName;
    }

    public ObservableList<Object> getOriginalRow() {
        return originalRow;
    }

    public ObservableList<Object> getCurrentRow() {
        return currentRow;
    }

    public Map<Integer, Object> getChangedColumns() {
        return changedColumns;
    }

    public java.util.List<String> getColumnNames() {
        return columnNames;
    }

    public int getPrimaryKeyIndex() {
        return primaryKeyIndex;
    }

    /**
     * Generates the SQL statement for this change.
     */
    public String toSql() {
        switch (type) {
            case INSERT:
                return generateInsertSql();
            case UPDATE:
                return generateUpdateSql();
            case DELETE:
                return generateDeleteSql();
            default:
                return null;
        }
    }

    private String generateInsertSql() {
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();

        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(columnNames.get(i));
            values.append(formatValue(currentRow.get(i)));
        }

        return String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName, columns, values);
    }

    private String generateUpdateSql() {
        if (changedColumns.isEmpty()) {
            return null;
        }

        StringBuilder setClause = new StringBuilder();
        boolean first = true;

        for (Map.Entry<Integer, Object> entry : changedColumns.entrySet()) {
            if (!first) {
                setClause.append(", ");
            }
            first = false;
            setClause.append(columnNames.get(entry.getKey()))
                    .append(" = ")
                    .append(formatValue(entry.getValue()));
        }

        String whereClause = buildWhereClause(originalRow);

        return String.format("UPDATE %s SET %s WHERE %s",
                tableName, setClause, whereClause);
    }

    private String generateDeleteSql() {
        String whereClause = buildWhereClause(originalRow);
        return String.format("DELETE FROM %s WHERE %s", tableName, whereClause);
    }

    private String buildWhereClause(ObservableList<Object> row) {
        // If we have a primary key, use it
        if (primaryKeyIndex >= 0 && primaryKeyIndex < row.size()) {
            Object pkValue = row.get(primaryKeyIndex);
            return columnNames.get(primaryKeyIndex) + " = " + formatValue(pkValue);
        }

        // Otherwise, use all columns (not ideal but works for simple cases)
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < Math.min(columnNames.size(), row.size()); i++) {
            if (i > 0) {
                where.append(" AND ");
            }
            Object value = row.get(i);
            if (value == null || "[NULL]".equals(value)) {
                where.append(columnNames.get(i)).append(" IS NULL");
            } else {
                where.append(columnNames.get(i)).append(" = ").append(formatValue(value));
            }
        }
        return where.toString();
    }

    private String formatValue(Object value) {
        if (value == null || "[NULL]".equals(value)) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "TRUE" : "FALSE";
        }
        // Escape single quotes for strings
        String strValue = value.toString().replace("'", "''");
        return "'" + strValue + "'";
    }
}
