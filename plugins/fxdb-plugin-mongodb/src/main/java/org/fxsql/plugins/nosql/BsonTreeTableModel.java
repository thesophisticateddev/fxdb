package org.fxsql.plugins.nosql;

import javafx.scene.control.TreeItem;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class BsonTreeTableModel {

    public record BsonField(String key, String value, String type) {}

    /**
     * Converts a single BSON Document to a tree root item.
     * The root is keyed by its _id or index.
     */
    public static TreeItem<BsonField> documentToTreeItem(Document doc, int index) {
        String idDisplay;
        Object id = doc.get("_id");
        if (id instanceof ObjectId oid) {
            idDisplay = oid.toHexString();
        } else if (id != null) {
            idDisplay = id.toString();
        } else {
            idDisplay = "doc[" + index + "]";
        }

        TreeItem<BsonField> root = new TreeItem<>(
                new BsonField(idDisplay, "{" + doc.size() + " fields}", "Document"));

        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            root.getChildren().add(convertEntry(entry.getKey(), entry.getValue()));
        }

        return root;
    }

    /**
     * Recursively converts a key-value pair to a TreeItem.
     */
    private static TreeItem<BsonField> convertEntry(String key, Object value) {
        if (value == null) {
            return new TreeItem<>(new BsonField(key, "null", "Null"));
        }

        if (value instanceof Document nested) {
            TreeItem<BsonField> item = new TreeItem<>(
                    new BsonField(key, "{" + nested.size() + " fields}", "Document"));
            for (Map.Entry<String, Object> entry : nested.entrySet()) {
                item.getChildren().add(convertEntry(entry.getKey(), entry.getValue()));
            }
            return item;
        }

        if (value instanceof List<?> list) {
            TreeItem<BsonField> item = new TreeItem<>(
                    new BsonField(key, "[" + list.size() + " elements]", "Array"));
            for (int i = 0; i < list.size(); i++) {
                item.getChildren().add(convertEntry("[" + i + "]", list.get(i)));
            }
            return item;
        }

        if (value instanceof ObjectId oid) {
            return new TreeItem<>(new BsonField(key, oid.toHexString(), "ObjectId"));
        }

        if (value instanceof String s) {
            return new TreeItem<>(new BsonField(key, "\"" + s + "\"", "String"));
        }

        if (value instanceof Integer || value instanceof Long) {
            return new TreeItem<>(new BsonField(key, value.toString(), "Integer"));
        }

        if (value instanceof Double || value instanceof Float) {
            return new TreeItem<>(new BsonField(key, value.toString(), "Double"));
        }

        if (value instanceof Boolean b) {
            return new TreeItem<>(new BsonField(key, b.toString(), "Boolean"));
        }

        if (value instanceof Date d) {
            return new TreeItem<>(new BsonField(key, d.toString(), "Date"));
        }

        // Fallback for other BSON types (Binary, Regex, Decimal128, etc.)
        return new TreeItem<>(new BsonField(key, value.toString(), value.getClass().getSimpleName()));
    }
}
