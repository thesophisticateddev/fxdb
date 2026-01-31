package org.fxsql.components.sqlScriptExecutor;

import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL Editor component with syntax highlighting.
 * Supports highlighting for:
 * - SQL keywords (SELECT, FROM, WHERE, etc.)
 * - Functions (COUNT, SUM, AVG, etc.)
 * - Operators (AND, OR, NOT, etc.)
 * - String literals ('text')
 * - Numbers
 * - Comments (-- and /*
 * */

public class SQLEditor extends VBox {

    // SQL Keywords
    private static final String[] KEYWORDS = new String[]{
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "CREATE", "DROP", "ALTER", "TABLE", "INDEX", "VIEW", "DATABASE",
            "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "FULL", "CROSS", "NATURAL",
            "ON", "USING", "AS", "DISTINCT", "ALL", "TOP", "LIMIT", "OFFSET",
            "ORDER", "BY", "ASC", "DESC", "NULLS", "FIRST", "LAST",
            "GROUP", "HAVING", "UNION", "INTERSECT", "EXCEPT", "MINUS",
            "EXISTS", "IN", "BETWEEN", "LIKE", "ILIKE", "ESCAPE", "SIMILAR",
            "CASE", "WHEN", "THEN", "ELSE", "END", "IF", "ELSEIF",
            "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "TRANSACTION",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "UNIQUE",
            "NOT", "NULL", "DEFAULT", "AUTO_INCREMENT", "SERIAL", "IDENTITY",
            "CHECK", "CASCADE", "RESTRICT", "NO", "ACTION",
            "GRANT", "REVOKE", "PRIVILEGES", "TO", "WITH", "RECURSIVE",
            "TRUNCATE", "MERGE", "UPSERT", "REPLACE", "EXPLAIN", "ANALYZE",
            "RETURNING", "CONFLICT", "DO", "NOTHING"
    };

    // SQL Functions
    private static final String[] FUNCTIONS = new String[]{
            "COUNT", "SUM", "AVG", "MIN", "MAX", "ABS", "ROUND", "CEIL", "FLOOR",
            "COALESCE", "NULLIF", "CAST", "CONVERT", "IFNULL", "NVL", "ISNULL",
            "CONCAT", "SUBSTRING", "SUBSTR", "LENGTH", "LEN", "CHAR_LENGTH",
            "UPPER", "LOWER", "TRIM", "LTRIM", "RTRIM", "REPLACE", "REVERSE",
            "LEFT", "RIGHT", "LPAD", "RPAD", "REPEAT", "SPACE", "POSITION",
            "NOW", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "GETDATE",
            "DATE", "TIME", "DATETIME", "TIMESTAMP", "YEAR", "MONTH", "DAY",
            "HOUR", "MINUTE", "SECOND", "DATEADD", "DATEDIFF", "DATE_PART",
            "EXTRACT", "TO_DATE", "TO_CHAR", "TO_NUMBER", "FORMAT",
            "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE", "LAG", "LEAD",
            "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE", "OVER", "PARTITION",
            "STRING_AGG", "ARRAY_AGG", "JSON_AGG", "LISTAGG",
            "GREATEST", "LEAST", "POWER", "SQRT", "MOD", "RANDOM", "UUID"
    };

    // Operators
    private static final String[] OPERATORS = new String[]{
            "AND", "OR", "NOT", "IS", "TRUE", "FALSE", "UNKNOWN"
    };

    // Data types
    private static final String[] DATATYPES = new String[]{
            "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "NUMERIC",
            "FLOAT", "REAL", "DOUBLE", "PRECISION", "BOOLEAN", "BOOL", "BIT",
            "CHAR", "VARCHAR", "TEXT", "NCHAR", "NVARCHAR", "NTEXT",
            "DATE", "TIME", "DATETIME", "TIMESTAMP", "INTERVAL",
            "BLOB", "CLOB", "BINARY", "VARBINARY", "BYTEA",
            "JSON", "JSONB", "XML", "UUID", "ARRAY", "ENUM"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String FUNCTION_PATTERN = "\\b(" + String.join("|", FUNCTIONS) + ")\\s*(?=\\()";
    private static final String OPERATOR_PATTERN = "\\b(" + String.join("|", OPERATORS) + ")\\b";
    private static final String DATATYPE_PATTERN = "\\b(" + String.join("|", DATATYPES) + ")\\b";
    private static final String STRING_PATTERN = "'([^'\\\\]|\\\\.)*'";
    private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?\\b";
    private static final String COMMENT_SINGLE_PATTERN = "--[^\n]*";
    private static final String COMMENT_MULTI_PATTERN = "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/";
    private static final String PAREN_PATTERN = "[()]";
    private static final String SEMICOLON_PATTERN = ";";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<COMMENT>" + COMMENT_SINGLE_PATTERN + "|" + COMMENT_MULTI_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<FUNCTION>" + FUNCTION_PATTERN + ")"
                    + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<OPERATOR>" + OPERATOR_PATTERN + ")"
                    + "|(?<DATATYPE>" + DATATYPE_PATTERN + ")"
                    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")",
            Pattern.CASE_INSENSITIVE
    );

    private final CodeArea codeArea;

    public SQLEditor() {
        this.codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("sql-editor");

        // Ensure the code area is editable and focusable
        codeArea.setEditable(true);
        codeArea.setFocusTraversable(true);

        // Apply highlighting on text changes
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.isEmpty()) {
                codeArea.setStyleSpans(0, computeHighlighting(newText));
            }
        });

        // Add the code area to this pane and make it grow
        this.getChildren().add(codeArea);
        VBox.setVgrow(codeArea, Priority.ALWAYS);

        // Set minimum sizes
        this.setMinHeight(100);
        this.setMinWidth(200);

        // Request focus when clicked on the container
        this.setOnMouseClicked(e -> codeArea.requestFocus());

        // Load stylesheet
        try {
            String css = Objects.requireNonNull(
                    getClass().getClassLoader().getResource("stylesheets/sql-editor.css")
            ).toExternalForm();
            this.getStylesheets().add(css);
        } catch (NullPointerException e) {
            System.err.println("Warning: sql-editor.css not found");
        }
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = null;

            if (matcher.group("COMMENT") != null) {
                styleClass = "comment";
            } else if (matcher.group("STRING") != null) {
                styleClass = "string";
            } else if (matcher.group("FUNCTION") != null) {
                styleClass = "function";
            } else if (matcher.group("KEYWORD") != null) {
                styleClass = "keyword";
            } else if (matcher.group("OPERATOR") != null) {
                styleClass = "operator";
            } else if (matcher.group("DATATYPE") != null) {
                styleClass = "datatype";
            } else if (matcher.group("NUMBER") != null) {
                styleClass = "number";
            } else if (matcher.group("PAREN") != null) {
                styleClass = "paren";
            } else if (matcher.group("SEMICOLON") != null) {
                styleClass = "semicolon";
            }

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(
                    styleClass != null ? Collections.singleton(styleClass) : Collections.emptyList(),
                    matcher.end() - matcher.start()
            );
            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Returns all SQL queries from the editor, split by semicolon.
     * Empty queries are filtered out.
     */
    public String[] sqlQueriesInEditor() {
        String sqlText = codeArea.getText();

        // Split by semicolon and trim each query
        return Arrays.stream(sqlText.split(";"))
                .map(String::trim)
                .filter(q -> !q.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Returns the currently selected text, or null if nothing is selected.
     */
    public String getSelectedText() {
        String selected = codeArea.getSelectedText();
        return (selected != null && !selected.trim().isEmpty()) ? selected.trim() : null;
    }

    /**
     * Sets the text content of the editor.
     */
    public void setText(String text) {
        codeArea.replaceText(text);
    }

    /**
     * Gets the full text content of the editor.
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Clears the editor content.
     */
    public void clear() {
        codeArea.clear();
    }

    /**
     * Returns the underlying CodeArea for advanced customization.
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }
}
