package org.fxsql.plugins.samples;

import org.fxsql.events.EventBus;
import org.fxsql.plugins.AbstractPlugin;
import org.fxsql.plugins.FXPlugin;
import org.fxsql.plugins.events.PluginEvent;
import org.fxsql.plugins.pluginHook.FXPluginStart;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sample Syntax Highlighter Plugin.
 * This is a template showing how to create a plugin for FXDB.
 *
 * To create your own plugin:
 * 1. Extend AbstractPlugin or implement IPlugin
 * 2. Add the @FXPlugin annotation with a unique id
 * 3. Implement the required lifecycle methods
 * 4. Package as a JAR and place in the plugins directory
 */
@FXPlugin(id = "syntax-highlighter-sample")
public class SampleSyntaxHighlighterPlugin extends AbstractPlugin {

    private static final String VERSION = "1.0.0";
    private static final String NAME = "Sample Syntax Highlighter";

    // SQL keyword patterns
    private final Map<String, Pattern> sqlPatterns = new HashMap<>();

    // Supported SQL dialects
    public enum SQLDialect {
        STANDARD, POSTGRESQL, MYSQL, SQLITE, ORACLE
    }

    private SQLDialect currentDialect = SQLDialect.STANDARD;

    @Override
    public String getId() {
        return "syntax-highlighter-sample";
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    protected void onInitialize() {
        logger.info("Initializing " + NAME);
        initializePatterns();
    }

    @FXPluginStart
    public void onPluginStart() {
        logger.info("Syntax Highlighter plugin started via @FXPluginStart");
    }

    @Override
    protected void onStart() {
        logger.info("Starting " + NAME);

        // Register event handlers for syntax highlighting requests
        // In a real plugin, this would integrate with the SQL editor

        // Emit event that we're ready
        EventBus.fireEvent(new PluginEvent(
                PluginEvent.PLUGIN_STARTED,
                "Syntax highlighting service is ready",
                getId()
        ));
    }

    @Override
    protected void onStop() {
        logger.info("Stopping " + NAME);
        sqlPatterns.clear();
    }

    private void initializePatterns() {
        // Standard SQL keywords
        String[] keywords = {
                "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE",
                "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
                "CREATE", "TABLE", "INDEX", "VIEW", "DROP", "ALTER",
                "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS", "ON",
                "GROUP", "BY", "ORDER", "ASC", "DESC", "HAVING", "LIMIT", "OFFSET",
                "UNION", "ALL", "DISTINCT", "AS", "NULL", "IS", "BETWEEN",
                "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END",
                "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT",
                "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION"
        };

        String keywordPattern = "\\b(" + String.join("|", keywords) + ")\\b";
        sqlPatterns.put("KEYWORD", Pattern.compile(keywordPattern, Pattern.CASE_INSENSITIVE));

        // String literals
        sqlPatterns.put("STRING", Pattern.compile("'[^']*'|\"[^\"]*\""));

        // Numbers
        sqlPatterns.put("NUMBER", Pattern.compile("\\b\\d+(\\.\\d+)?\\b"));

        // Comments
        sqlPatterns.put("COMMENT_LINE", Pattern.compile("--.*$", Pattern.MULTILINE));
        sqlPatterns.put("COMMENT_BLOCK", Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL));

        // Operators
        sqlPatterns.put("OPERATOR", Pattern.compile("[=<>!]+|\\+|-|\\*|/|%"));

        logger.info("Initialized " + sqlPatterns.size() + " syntax patterns");
    }

    /**
     * Sets the SQL dialect for highlighting.
     */
    public void setDialect(SQLDialect dialect) {
        this.currentDialect = dialect;
        logger.info("SQL dialect set to: " + dialect);
    }

    /**
     * Returns the pattern for a given token type.
     */
    public Pattern getPattern(String tokenType) {
        return sqlPatterns.get(tokenType);
    }

    /**
     * Returns all available token types.
     */
    public String[] getTokenTypes() {
        return sqlPatterns.keySet().toArray(new String[0]);
    }

    /**
     * Analyzes SQL and returns token positions.
     * This is a simplified example - real implementation would return styled spans.
     */
    public Map<String, int[][]> analyzeSql(String sql) {
        Map<String, int[][]> tokenPositions = new HashMap<>();

        for (Map.Entry<String, Pattern> entry : sqlPatterns.entrySet()) {
            java.util.regex.Matcher matcher = entry.getValue().matcher(sql);
            java.util.List<int[]> positions = new java.util.ArrayList<>();

            while (matcher.find()) {
                positions.add(new int[]{matcher.start(), matcher.end()});
            }

            if (!positions.isEmpty()) {
                tokenPositions.put(entry.getKey(), positions.toArray(new int[0][]));
            }
        }

        return tokenPositions;
    }
}