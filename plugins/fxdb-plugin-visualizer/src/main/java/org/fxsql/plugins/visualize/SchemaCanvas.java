package org.fxsql.plugins.visualize;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.fxsql.model.TableMetaData;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SchemaCanvas extends Pane {

    private static final Logger logger = Logger.getLogger(SchemaCanvas.class.getName());

    private static final double TABLE_WIDTH = 220;
    private static final double HEADER_HEIGHT = 30;
    private static final double ROW_HEIGHT = 22;
    private static final double TABLE_PADDING = 40;
    private static final double INITIAL_X = 30;
    private static final double INITIAL_Y = 30;

    private static final Font HEADER_FONT = Font.font("System", FontWeight.BOLD, 13);
    private static final Font COLUMN_FONT = Font.font("System", FontWeight.NORMAL, 12);
    private static final Font BADGE_FONT = Font.font("System", FontWeight.BOLD, 10);

    private static final Color HEADER_BG = Color.rgb(55, 71, 133);
    private static final Color HEADER_TEXT = Color.WHITE;
    private static final Color TABLE_BG = Color.rgb(245, 245, 250);
    private static final Color TABLE_BORDER = Color.rgb(180, 180, 200);
    private static final Color PK_COLOR = Color.rgb(218, 165, 32);
    private static final Color FK_COLOR = Color.rgb(70, 130, 180);
    private static final Color COLUMN_TEXT = Color.rgb(50, 50, 50);
    private static final Color TYPE_TEXT = Color.rgb(120, 120, 140);
    private static final Color RELATIONSHIP_LINE = Color.rgb(70, 130, 180, 0.7);

    private final Canvas canvas;
    private final List<TableBox> tableBoxes = new ArrayList<>();
    private final List<Relationship> relationships = new ArrayList<>();

    private double offsetX = 0;
    private double offsetY = 0;
    private double zoom = 1.0;
    private double dragStartX, dragStartY;
    private double dragOffsetX, dragOffsetY;
    private int tableCount = 0;

    public SchemaCanvas() {
        canvas = new Canvas(2000, 2000);
        getChildren().add(canvas);

        setupMouseHandlers();
    }

    private void setupMouseHandlers() {
        // Pan with mouse drag
        canvas.setOnMousePressed(e -> {
            dragStartX = e.getScreenX();
            dragStartY = e.getScreenY();
            dragOffsetX = offsetX;
            dragOffsetY = offsetY;
        });

        canvas.setOnMouseDragged(e -> {
            offsetX = dragOffsetX + (e.getScreenX() - dragStartX);
            offsetY = dragOffsetY + (e.getScreenY() - dragStartY);
            redraw();
        });

        // Zoom with scroll
        canvas.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 0.9;
            double newZoom = zoom * factor;
            if (newZoom >= 0.3 && newZoom <= 3.0) {
                zoom = newZoom;
                redraw();
            }
        });
    }

    public void loadSchema(Connection jdbcConnection) {
        tableBoxes.clear();
        relationships.clear();
        tableCount = 0;

        if (jdbcConnection == null) {
            redraw();
            return;
        }

        try {
            boolean isSqlite = isSqlite(jdbcConnection);
            DatabaseMetaData dbMeta = jdbcConnection.getMetaData();
            List<String> tableNames = isSqlite
                    ? discoverTablesSqlite(jdbcConnection)
                    : discoverTables(dbMeta);

            Map<String, TableMetaData> metadataMap = new LinkedHashMap<>();

            for (String tableName : tableNames) {
                try {
                    TableMetaData meta = isSqlite
                            ? buildTableMetaDataSqlite(jdbcConnection, tableName)
                            : buildTableMetaData(dbMeta, tableName);
                    metadataMap.put(tableName, meta);
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Failed to load metadata for table: " + tableName, e);
                }
            }

            layoutTables(metadataMap);
            buildRelationships(metadataMap);
            tableCount = metadataMap.size();
            redraw();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load schema", e);
        }
    }

    private boolean isSqlite(Connection conn) {
        try {
            String product = conn.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("sqlite");
        } catch (SQLException e) {
            return false;
        }
    }

    private List<String> discoverTables(DatabaseMetaData dbMeta) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = dbMeta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private List<String> discoverTablesSqlite(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        }
        return tables;
    }

    private TableMetaData buildTableMetaData(DatabaseMetaData dbMeta, String tableName) throws SQLException {
        TableMetaData metadata = new TableMetaData(tableName);

        // Columns
        try (ResultSet rs = dbMeta.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                TableMetaData.ColumnInfo col = new TableMetaData.ColumnInfo(rs.getString("COLUMN_NAME"));
                col.setTypeName(rs.getString("TYPE_NAME"));
                col.setSize(rs.getInt("COLUMN_SIZE"));
                col.setDecimalDigits(rs.getInt("DECIMAL_DIGITS"));
                col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                metadata.getColumns().add(col);
            }
        }

        // Primary keys
        try (ResultSet rs = dbMeta.getPrimaryKeys(null, null, tableName)) {
            while (rs.next()) {
                TableMetaData.PrimaryKeyInfo pk = new TableMetaData.PrimaryKeyInfo(rs.getString("COLUMN_NAME"));
                pk.setPkName(rs.getString("PK_NAME"));
                metadata.getPrimaryKeys().add(pk);
            }
        }

        // Foreign keys
        try (ResultSet rs = dbMeta.getImportedKeys(null, null, tableName)) {
            while (rs.next()) {
                TableMetaData.ForeignKeyInfo fk = new TableMetaData.ForeignKeyInfo(rs.getString("FKCOLUMN_NAME"));
                fk.setFkName(rs.getString("FK_NAME"));
                fk.setPkTableName(rs.getString("PKTABLE_NAME"));
                fk.setPkColumnName(rs.getString("PKCOLUMN_NAME"));
                metadata.getForeignKeys().add(fk);
            }
        }

        return metadata;
    }

    private TableMetaData buildTableMetaDataSqlite(Connection conn, String tableName) throws SQLException {
        TableMetaData metadata = new TableMetaData(tableName);

        // Columns via PRAGMA table_info
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info('" + tableName.replace("'", "''") + "')")) {
            while (rs.next()) {
                String colName = rs.getString("name");
                String colType = rs.getString("type");
                boolean notNull = rs.getInt("notnull") == 1;
                boolean pk = rs.getInt("pk") > 0;

                TableMetaData.ColumnInfo col = new TableMetaData.ColumnInfo(colName);
                col.setTypeName(colType != null ? colType : "");
                col.setNullable(!notNull);
                metadata.getColumns().add(col);

                if (pk) {
                    TableMetaData.PrimaryKeyInfo pkInfo = new TableMetaData.PrimaryKeyInfo(colName);
                    pkInfo.setPkName("pk_" + tableName);
                    metadata.getPrimaryKeys().add(pkInfo);
                }
            }
        }

        // Foreign keys via PRAGMA foreign_key_list
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list('" + tableName.replace("'", "''") + "')")) {
            while (rs.next()) {
                String fkCol = rs.getString("from");
                String pkTable = rs.getString("table");
                String pkCol = rs.getString("to");

                TableMetaData.ForeignKeyInfo fk = new TableMetaData.ForeignKeyInfo(fkCol);
                fk.setFkName("fk_" + tableName + "_" + fkCol);
                fk.setPkTableName(pkTable);
                fk.setPkColumnName(pkCol);
                metadata.getForeignKeys().add(fk);
            }
        }

        return metadata;
    }

    private void layoutTables(Map<String, TableMetaData> metadataMap) {
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(metadataMap.size())));
        int col = 0;
        int row = 0;
        double maxHeightInRow = 0;
        double currentY = INITIAL_Y;

        for (Map.Entry<String, TableMetaData> entry : metadataMap.entrySet()) {
            TableMetaData meta = entry.getValue();
            double tableHeight = HEADER_HEIGHT + (meta.getColumns().size() * ROW_HEIGHT) + 4;

            double x = INITIAL_X + col * (TABLE_WIDTH + TABLE_PADDING);
            double y = currentY;

            Set<String> pkColumns = new HashSet<>();
            for (TableMetaData.PrimaryKeyInfo pk : meta.getPrimaryKeys()) {
                pkColumns.add(pk.getColumnName());
            }
            Set<String> fkColumns = new HashSet<>();
            for (TableMetaData.ForeignKeyInfo fk : meta.getForeignKeys()) {
                fkColumns.add(fk.getFkColumnName());
            }

            tableBoxes.add(new TableBox(meta, x, y, TABLE_WIDTH, tableHeight, pkColumns, fkColumns));

            maxHeightInRow = Math.max(maxHeightInRow, tableHeight);
            col++;
            if (col >= columns) {
                col = 0;
                row++;
                currentY += maxHeightInRow + TABLE_PADDING;
                maxHeightInRow = 0;
            }
        }
    }

    private void buildRelationships(Map<String, TableMetaData> metadataMap) {
        Map<String, TableBox> boxByName = new HashMap<>();
        for (TableBox box : tableBoxes) {
            boxByName.put(box.metadata.getTableName(), box);
        }

        for (TableBox box : tableBoxes) {
            for (TableMetaData.ForeignKeyInfo fk : box.metadata.getForeignKeys()) {
                TableBox target = boxByName.get(fk.getPkTableName());
                if (target != null) {
                    relationships.add(new Relationship(box, target, fk.getFkColumnName(), fk.getPkColumnName()));
                }
            }
        }
    }

    public void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.save();
        gc.translate(offsetX, offsetY);
        gc.scale(zoom, zoom);

        // Draw relationship lines first (behind tables)
        for (Relationship rel : relationships) {
            drawRelationship(gc, rel);
        }

        // Draw tables
        for (TableBox box : tableBoxes) {
            drawTable(gc, box);
        }

        gc.restore();
    }

    private void drawTable(GraphicsContext gc, TableBox box) {
        double x = box.x;
        double y = box.y;
        double w = box.width;
        double h = box.height;

        // Shadow
        gc.setFill(Color.rgb(0, 0, 0, 0.1));
        gc.fillRoundRect(x + 3, y + 3, w, h, 8, 8);

        // Table background
        gc.setFill(TABLE_BG);
        gc.fillRoundRect(x, y, w, h, 8, 8);
        gc.setStroke(TABLE_BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, w, h, 8, 8);

        // Header
        gc.setFill(HEADER_BG);
        gc.fillRoundRect(x, y, w, HEADER_HEIGHT, 8, 8);
        // Square off the bottom corners of the header
        gc.fillRect(x, y + HEADER_HEIGHT - 8, w, 8);

        gc.setFill(HEADER_TEXT);
        gc.setFont(HEADER_FONT);
        gc.fillText(box.metadata.getTableName(), x + 10, y + 20);

        // Separator line
        gc.setStroke(TABLE_BORDER);
        gc.strokeLine(x, y + HEADER_HEIGHT, x + w, y + HEADER_HEIGHT);

        // Columns
        double rowY = y + HEADER_HEIGHT + 2;
        for (TableMetaData.ColumnInfo col : box.metadata.getColumns()) {
            boolean isPk = box.pkColumns.contains(col.getName());
            boolean isFk = box.fkColumns.contains(col.getName());

            // Row highlight for PK/FK
            if (isPk || isFk) {
                gc.setFill(isPk ? Color.rgb(255, 248, 220, 0.5) : Color.rgb(220, 235, 250, 0.5));
                gc.fillRect(x + 1, rowY, w - 2, ROW_HEIGHT);
            }

            // PK/FK badge
            double textX = x + 8;
            if (isPk) {
                gc.setFont(BADGE_FONT);
                gc.setFill(PK_COLOR);
                gc.fillText("PK", textX, rowY + 15);
                textX += 22;
            }
            if (isFk) {
                gc.setFont(BADGE_FONT);
                gc.setFill(FK_COLOR);
                gc.fillText("FK", textX, rowY + 15);
                textX += 22;
            }
            if (!isPk && !isFk) {
                textX += 4;
            }

            // Column name
            gc.setFont(COLUMN_FONT);
            gc.setFill(COLUMN_TEXT);
            gc.fillText(col.getName(), textX, rowY + 15);

            // Column type (right-aligned)
            String type = col.getFormattedType() != null ? col.getFormattedType() : "";
            gc.setFill(TYPE_TEXT);
            gc.setFont(Font.font("System", FontWeight.NORMAL, 10));
            gc.fillText(type, x + w - 8 - type.length() * 5.5, rowY + 15);

            rowY += ROW_HEIGHT;
        }
    }

    private void drawRelationship(GraphicsContext gc, Relationship rel) {
        // Calculate connection points (right side of source, left side of target)
        double srcX = rel.source.x + rel.source.width;
        double srcY = rel.source.y + HEADER_HEIGHT / 2;
        double tgtX = rel.target.x;
        double tgtY = rel.target.y + HEADER_HEIGHT / 2;

        // If target is to the left, flip the connection points
        if (rel.target.x + rel.target.width < rel.source.x) {
            srcX = rel.source.x;
            tgtX = rel.target.x + rel.target.width;
        }
        // If tables overlap horizontally, connect top/bottom
        if (Math.abs(rel.source.x - rel.target.x) < TABLE_WIDTH) {
            srcX = rel.source.x + rel.source.width / 2;
            srcY = rel.source.y + rel.source.height;
            tgtX = rel.target.x + rel.target.width / 2;
            tgtY = rel.target.y;
            if (rel.source.y > rel.target.y) {
                srcY = rel.source.y;
                tgtY = rel.target.y + rel.target.height;
            }
        }

        gc.setStroke(RELATIONSHIP_LINE);
        gc.setLineWidth(1.5);
        gc.setLineDashes(6, 3);

        // Draw a curved line
        double midX = (srcX + tgtX) / 2;
        gc.beginPath();
        gc.moveTo(srcX, srcY);
        gc.bezierCurveTo(midX, srcY, midX, tgtY, tgtX, tgtY);
        gc.stroke();

        gc.setLineDashes(null);

        // Draw an arrowhead at the target end
        drawArrowhead(gc, midX, tgtY, tgtX, tgtY);

        // Draw a small diamond at the source end (many-to-one indicator)
        drawDiamond(gc, srcX, srcY);
    }

    private void drawArrowhead(GraphicsContext gc, double fromX, double fromY, double toX, double toY) {
        double angle = Math.atan2(toY - fromY, toX - fromX);
        double arrowLength = 10;
        double arrowAngle = Math.toRadians(25);

        double x1 = toX - arrowLength * Math.cos(angle - arrowAngle);
        double y1 = toY - arrowLength * Math.sin(angle - arrowAngle);
        double x2 = toX - arrowLength * Math.cos(angle + arrowAngle);
        double y2 = toY - arrowLength * Math.sin(angle + arrowAngle);

        gc.setFill(RELATIONSHIP_LINE);
        gc.fillPolygon(new double[]{toX, x1, x2}, new double[]{toY, y1, y2}, 3);
    }

    private void drawDiamond(GraphicsContext gc, double cx, double cy) {
        double size = 5;
        gc.setFill(RELATIONSHIP_LINE);
        gc.fillPolygon(
                new double[]{cx, cx + size, cx, cx - size},
                new double[]{cy - size, cy, cy + size, cy},
                4
        );
    }

    public void zoomIn() {
        if (zoom < 3.0) {
            zoom *= 1.2;
            redraw();
        }
    }

    public void zoomOut() {
        if (zoom > 0.3) {
            zoom /= 1.2;
            redraw();
        }
    }

    public void resetView() {
        zoom = 1.0;
        offsetX = 0;
        offsetY = 0;
        redraw();
    }

    public int getTableCount() {
        return tableCount;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public void resizeCanvas(double width, double height) {
        canvas.setWidth(width);
        canvas.setHeight(height);
        redraw();
    }

    // Internal data structures

    private static class TableBox {
        final TableMetaData metadata;
        final double x, y, width, height;
        final Set<String> pkColumns;
        final Set<String> fkColumns;

        TableBox(TableMetaData metadata, double x, double y, double width, double height,
                 Set<String> pkColumns, Set<String> fkColumns) {
            this.metadata = metadata;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.pkColumns = pkColumns;
            this.fkColumns = fkColumns;
        }
    }

    private static class Relationship {
        final TableBox source;
        final TableBox target;
        final String fkColumn;
        final String pkColumn;

        Relationship(TableBox source, TableBox target, String fkColumn, String pkColumn) {
            this.source = source;
            this.target = target;
            this.fkColumn = fkColumn;
            this.pkColumn = pkColumn;
        }
    }
}
