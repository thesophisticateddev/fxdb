package org.fxsql.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents metadata about a database table including columns, primary keys, and foreign keys.
 */
public class TableMetaData {

    private String tableName;
    private String schema;
    private String catalog;
    private String tableType;
    private List<ColumnInfo> columns;
    private List<PrimaryKeyInfo> primaryKeys;
    private List<ForeignKeyInfo> foreignKeys;
    private List<IndexInfo> indexes;

    public TableMetaData(String tableName) {
        this.tableName = tableName;
        this.columns = new ArrayList<>();
        this.primaryKeys = new ArrayList<>();
        this.foreignKeys = new ArrayList<>();
        this.indexes = new ArrayList<>();
    }

    // Getters and setters
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getCatalog() { return catalog; }
    public void setCatalog(String catalog) { this.catalog = catalog; }

    public String getTableType() { return tableType; }
    public void setTableType(String tableType) { this.tableType = tableType; }

    public List<ColumnInfo> getColumns() { return columns; }
    public void setColumns(List<ColumnInfo> columns) { this.columns = columns; }

    public List<PrimaryKeyInfo> getPrimaryKeys() { return primaryKeys; }
    public void setPrimaryKeys(List<PrimaryKeyInfo> primaryKeys) { this.primaryKeys = primaryKeys; }

    public List<ForeignKeyInfo> getForeignKeys() { return foreignKeys; }
    public void setForeignKeys(List<ForeignKeyInfo> foreignKeys) { this.foreignKeys = foreignKeys; }

    public List<IndexInfo> getIndexes() { return indexes; }
    public void setIndexes(List<IndexInfo> indexes) { this.indexes = indexes; }

    /**
     * Information about a table column.
     */
    public static class ColumnInfo {
        private String name;
        private String typeName;
        private int dataType;
        private int size;
        private int decimalDigits;
        private boolean nullable;
        private String defaultValue;
        private boolean autoIncrement;
        private int ordinalPosition;
        private String remarks;

        public ColumnInfo(String name) {
            this.name = name;
        }

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getTypeName() { return typeName; }
        public void setTypeName(String typeName) { this.typeName = typeName; }

        public int getDataType() { return dataType; }
        public void setDataType(int dataType) { this.dataType = dataType; }

        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }

        public int getDecimalDigits() { return decimalDigits; }
        public void setDecimalDigits(int decimalDigits) { this.decimalDigits = decimalDigits; }

        public boolean isNullable() { return nullable; }
        public void setNullable(boolean nullable) { this.nullable = nullable; }

        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

        public boolean isAutoIncrement() { return autoIncrement; }
        public void setAutoIncrement(boolean autoIncrement) { this.autoIncrement = autoIncrement; }

        public int getOrdinalPosition() { return ordinalPosition; }
        public void setOrdinalPosition(int ordinalPosition) { this.ordinalPosition = ordinalPosition; }

        public String getRemarks() { return remarks; }
        public void setRemarks(String remarks) { this.remarks = remarks; }

        /**
         * Returns a formatted type string including size.
         */
        public String getFormattedType() {
            if (size > 0) {
                if (decimalDigits > 0) {
                    return typeName + "(" + size + "," + decimalDigits + ")";
                }
                return typeName + "(" + size + ")";
            }
            return typeName;
        }
    }

    /**
     * Information about a primary key.
     */
    public static class PrimaryKeyInfo {
        private String columnName;
        private String pkName;
        private int keySeq;

        public PrimaryKeyInfo(String columnName) {
            this.columnName = columnName;
        }

        // Getters and setters
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }

        public String getPkName() { return pkName; }
        public void setPkName(String pkName) { this.pkName = pkName; }

        public int getKeySeq() { return keySeq; }
        public void setKeySeq(int keySeq) { this.keySeq = keySeq; }
    }

    /**
     * Information about a foreign key.
     */
    public static class ForeignKeyInfo {
        private String fkName;
        private String fkColumnName;
        private String pkTableName;
        private String pkColumnName;
        private int keySeq;
        private int updateRule;
        private int deleteRule;

        public ForeignKeyInfo(String fkColumnName) {
            this.fkColumnName = fkColumnName;
        }

        // Getters and setters
        public String getFkName() { return fkName; }
        public void setFkName(String fkName) { this.fkName = fkName; }

        public String getFkColumnName() { return fkColumnName; }
        public void setFkColumnName(String fkColumnName) { this.fkColumnName = fkColumnName; }

        public String getPkTableName() { return pkTableName; }
        public void setPkTableName(String pkTableName) { this.pkTableName = pkTableName; }

        public String getPkColumnName() { return pkColumnName; }
        public void setPkColumnName(String pkColumnName) { this.pkColumnName = pkColumnName; }

        public int getKeySeq() { return keySeq; }
        public void setKeySeq(int keySeq) { this.keySeq = keySeq; }

        public int getUpdateRule() { return updateRule; }
        public void setUpdateRule(int updateRule) { this.updateRule = updateRule; }

        public int getDeleteRule() { return deleteRule; }
        public void setDeleteRule(int deleteRule) { this.deleteRule = deleteRule; }

        /**
         * Returns a human-readable description of the foreign key reference.
         */
        public String getReferenceDescription() {
            return fkColumnName + " -> " + pkTableName + "(" + pkColumnName + ")";
        }

        /**
         * Returns a human-readable update rule.
         */
        public String getUpdateRuleDescription() {
            return getRuleDescription(updateRule);
        }

        /**
         * Returns a human-readable delete rule.
         */
        public String getDeleteRuleDescription() {
            return getRuleDescription(deleteRule);
        }

        private String getRuleDescription(int rule) {
            return switch (rule) {
                case 0 -> "CASCADE";
                case 1 -> "RESTRICT";
                case 2 -> "SET NULL";
                case 3 -> "NO ACTION";
                case 4 -> "SET DEFAULT";
                default -> "UNKNOWN";
            };
        }
    }

    /**
     * Information about an index.
     */
    public static class IndexInfo {
        private String indexName;
        private String columnName;
        private boolean nonUnique;
        private String indexType;
        private int ordinalPosition;
        private String ascOrDesc;

        public IndexInfo(String indexName) {
            this.indexName = indexName;
        }

        // Getters and setters
        public String getIndexName() { return indexName; }
        public void setIndexName(String indexName) { this.indexName = indexName; }

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }

        public boolean isNonUnique() { return nonUnique; }
        public void setNonUnique(boolean nonUnique) { this.nonUnique = nonUnique; }

        public String getIndexType() { return indexType; }
        public void setIndexType(String indexType) { this.indexType = indexType; }

        public int getOrdinalPosition() { return ordinalPosition; }
        public void setOrdinalPosition(int ordinalPosition) { this.ordinalPosition = ordinalPosition; }

        public String getAscOrDesc() { return ascOrDesc; }
        public void setAscOrDesc(String ascOrDesc) { this.ascOrDesc = ascOrDesc; }

        public String getUniqueDescription() {
            return nonUnique ? "Non-Unique" : "Unique";
        }
    }
}
