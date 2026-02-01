package org.fxsql.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents a column definition for creating a new table.
 */
public class ColumnDefinition {

    private final StringProperty columnName = new SimpleStringProperty("");
    private final StringProperty dataType = new SimpleStringProperty("VARCHAR(255)");
    private final BooleanProperty primaryKey = new SimpleBooleanProperty(false);
    private final BooleanProperty notNull = new SimpleBooleanProperty(false);
    private final BooleanProperty unique = new SimpleBooleanProperty(false);
    private final StringProperty defaultValue = new SimpleStringProperty("");

    public ColumnDefinition() {
    }

    public ColumnDefinition(String columnName, String dataType) {
        this.columnName.set(columnName);
        this.dataType.set(dataType);
    }

    // Column Name
    public String getColumnName() {
        return columnName.get();
    }

    public void setColumnName(String value) {
        columnName.set(value);
    }

    public StringProperty columnNameProperty() {
        return columnName;
    }

    // Data Type
    public String getDataType() {
        return dataType.get();
    }

    public void setDataType(String value) {
        dataType.set(value);
    }

    public StringProperty dataTypeProperty() {
        return dataType;
    }

    // Primary Key
    public boolean isPrimaryKey() {
        return primaryKey.get();
    }

    public void setPrimaryKey(boolean value) {
        primaryKey.set(value);
    }

    public BooleanProperty primaryKeyProperty() {
        return primaryKey;
    }

    // Not Null
    public boolean isNotNull() {
        return notNull.get();
    }

    public void setNotNull(boolean value) {
        notNull.set(value);
    }

    public BooleanProperty notNullProperty() {
        return notNull;
    }

    // Unique
    public boolean isUnique() {
        return unique.get();
    }

    public void setUnique(boolean value) {
        unique.set(value);
    }

    public BooleanProperty uniqueProperty() {
        return unique;
    }

    // Default Value
    public String getDefaultValue() {
        return defaultValue.get();
    }

    public void setDefaultValue(String value) {
        defaultValue.set(value);
    }

    public StringProperty defaultValueProperty() {
        return defaultValue;
    }

    /**
     * Generates the SQL column definition string.
     */
    public String toSqlDefinition() {
        StringBuilder sb = new StringBuilder();
        sb.append(getColumnName()).append(" ").append(getDataType());

        if (isPrimaryKey()) {
            sb.append(" PRIMARY KEY");
        }
        if (isNotNull() && !isPrimaryKey()) {
            sb.append(" NOT NULL");
        }
        if (isUnique() && !isPrimaryKey()) {
            sb.append(" UNIQUE");
        }
        if (getDefaultValue() != null && !getDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(getDefaultValue());
        }

        return sb.toString();
    }

    /**
     * Validates this column definition.
     */
    public boolean isValid() {
        return getColumnName() != null && !getColumnName().trim().isEmpty()
                && getDataType() != null && !getDataType().trim().isEmpty();
    }
}