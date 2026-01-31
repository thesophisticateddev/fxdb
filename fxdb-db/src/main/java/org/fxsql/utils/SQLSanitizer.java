package org.fxsql.utils;

import java.util.regex.Pattern;

/**
 * Utility class for SQL query sanitization and validation.
 * Helps prevent SQL injection and validates query structure.
 */
public final class SQLSanitizer {

    // Pattern to detect potential SQL injection attempts
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)(;\\s*(DROP|DELETE|TRUNCATE|ALTER|CREATE|INSERT|UPDATE)\\s)" +
                    "|(--\\s*$)" +
                    "|(\\b(UNION|UNION\\s+ALL)\\s+SELECT\\b)" +
                    "|(\\bEXEC(UTE)?\\s+(XP_|SP_))" +
                    "|(\\bINTO\\s+(OUTFILE|DUMPFILE)\\b)" +
                    "|(\\bLOAD_FILE\\s*\\()" +
                    "|(\\bBENCHMARK\\s*\\()" +
                    "|(\\bSLEEP\\s*\\()"
    );

    // Pattern to detect multiple statements (potential batch injection)
    private static final Pattern MULTI_STATEMENT_PATTERN = Pattern.compile(
            ";\\s*(?=\\S)"
    );

    // Dangerous keywords that should trigger warnings
    private static final String[] DANGEROUS_KEYWORDS = {
            "DROP DATABASE", "DROP TABLE", "DROP SCHEMA",
            "TRUNCATE TABLE", "DELETE FROM", "ALTER TABLE",
            "GRANT", "REVOKE", "CREATE USER", "DROP USER"
    };

    private SQLSanitizer() {
        // Utility class - prevent instantiation
    }

    /**
     * Result of SQL validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final boolean warning;
        private final String message;
        private final String sanitizedQuery;

        public ValidationResult(boolean valid, boolean warning, String message, String sanitizedQuery) {
            this.valid = valid;
            this.warning = warning;
            this.message = message;
            this.sanitizedQuery = sanitizedQuery;
        }

        public boolean isValid() { return valid; }
        public boolean hasWarning() { return warning; }
        public String getMessage() { return message; }
        public String getSanitizedQuery() { return sanitizedQuery; }
    }

    /**
     * Validates and sanitizes a SQL query.
     *
     * @param query The SQL query to validate
     * @param allowMultipleStatements Whether to allow multiple statements
     * @return ValidationResult containing the result of validation
     */
    public static ValidationResult validateQuery(String query, boolean allowMultipleStatements) {
        if (query == null || query.trim().isEmpty()) {
            return new ValidationResult(false, false, "Query is empty", null);
        }

        String trimmedQuery = query.trim();

        // Check for potential SQL injection patterns
        if (INJECTION_PATTERN.matcher(trimmedQuery).find()) {
            return new ValidationResult(false, false,
                    "Query contains potentially dangerous patterns that could indicate SQL injection",
                    null);
        }

        // Check for multiple statements if not allowed
        if (!allowMultipleStatements && MULTI_STATEMENT_PATTERN.matcher(trimmedQuery).find()) {
            return new ValidationResult(false, false,
                    "Query contains multiple statements. Please execute one statement at a time or use the script executor.",
                    null);
        }

        // Check for dangerous operations (warning only)
        String upperQuery = trimmedQuery.toUpperCase();
        for (String dangerous : DANGEROUS_KEYWORDS) {
            if (upperQuery.contains(dangerous)) {
                return new ValidationResult(true, true,
                        "Warning: Query contains '" + dangerous + "'. This operation may cause data loss.",
                        trimmedQuery);
            }
        }

        // Sanitize the query (basic cleanup)
        String sanitized = sanitizeQuery(trimmedQuery);

        return new ValidationResult(true, false, "Query is valid", sanitized);
    }

    /**
     * Sanitizes a SQL query by removing potentially harmful elements.
     *
     * @param query The query to sanitize
     * @return The sanitized query
     */
    public static String sanitizeQuery(String query) {
        if (query == null) return null;

        String sanitized = query.trim();

        // Remove null bytes
        sanitized = sanitized.replace("\0", "");

        // Normalize whitespace (but preserve structure)
        sanitized = sanitized.replaceAll("\\s+", " ");

        // Remove trailing semicolons for single queries
        if (sanitized.endsWith(";")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
        }

        return sanitized;
    }

    /**
     * Checks if a query is a read-only (SELECT) query.
     *
     * @param query The query to check
     * @return true if the query is read-only
     */
    public static boolean isReadOnlyQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String upper = query.trim().toUpperCase();
        return upper.startsWith("SELECT") ||
                upper.startsWith("WITH") ||
                upper.startsWith("SHOW") ||
                upper.startsWith("DESCRIBE") ||
                upper.startsWith("EXPLAIN");
    }

    /**
     * Checks if a query is a write/modification query.
     *
     * @param query The query to check
     * @return true if the query modifies data
     */
    public static boolean isWriteQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String upper = query.trim().toUpperCase();
        return upper.startsWith("INSERT") ||
                upper.startsWith("UPDATE") ||
                upper.startsWith("DELETE") ||
                upper.startsWith("CREATE") ||
                upper.startsWith("DROP") ||
                upper.startsWith("ALTER") ||
                upper.startsWith("TRUNCATE") ||
                upper.startsWith("MERGE") ||
                upper.startsWith("UPSERT") ||
                upper.startsWith("REPLACE");
    }

    /**
     * Escapes special characters in a string value for safe SQL inclusion.
     * Note: Prefer using PreparedStatement with parameters instead of this method.
     *
     * @param value The string value to escape
     * @return The escaped string
     */
    public static String escapeString(String value) {
        if (value == null) return null;

        return value
                .replace("\\", "\\\\")
                .replace("'", "''")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\0", "");
    }

    /**
     * Validates a table name to prevent injection.
     *
     * @param tableName The table name to validate
     * @return true if the table name is valid
     */
    public static boolean isValidIdentifier(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }

        // Allow alphanumeric, underscore, and dot (for schema.table)
        // First character must be letter or underscore
        return tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");
    }
}