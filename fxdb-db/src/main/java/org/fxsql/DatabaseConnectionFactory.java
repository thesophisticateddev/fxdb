package org.fxsql;

import java.util.logging.Logger;

/**
 * Factory for creating database connections based on database type.
 * Supports specialized connections for common databases and a generic
 * JDBC connection for others.
 */
public class DatabaseConnectionFactory {

    private static final Logger logger = Logger.getLogger(DatabaseConnectionFactory.class.getName());

    /**
     * Creates a DatabaseConnection instance based on the database type.
     *
     * @param databaseType The type of database (e.g., "sqlite", "mysql", "postgresql")
     * @return A DatabaseConnection instance for the specified type
     */
    public static DatabaseConnection getConnection(String databaseType) {
        if (databaseType == null || databaseType.isEmpty()) {
            throw new IllegalArgumentException("Database type cannot be null or empty");
        }

        String type = databaseType.toLowerCase().trim();

        // Handle cases where a JDBC URL was stored as the database type
        // e.g. "jdbc:sqlite:/path/to/db" → "sqlite"
        if (type.startsWith("jdbc:")) {
            String[] parts = type.substring(5).split(":", 2);
            if (parts.length > 0 && !parts[0].isEmpty()) {
                logger.info("Extracted database type '" + parts[0] + "' from JDBC URL: " + databaseType);
                type = parts[0];
            }
        }

        return switch (type) {
            // SQLite - file-based database
            case "sqlite" -> new SqliteConnection();

            // MySQL
            case "mysql" -> new MySqlConnection();

            // PostgreSQL (multiple naming conventions)
            case "postgres", "postgresql" -> new PostgresSqlConnection();

            // MariaDB - uses MySQL protocol
            case "mariadb" -> {
                logger.info("Using MySQL connection for MariaDB");
                yield new MySqlConnection();
            }

            // CockroachDB and TimescaleDB - PostgreSQL compatible
            case "cockroachdb", "timescaledb" -> {
                logger.info("Using PostgreSQL connection for " + databaseType);
                yield new PostgresSqlConnection();
            }

            // All other databases use generic JDBC connection
            default -> {
                logger.info("Using generic JDBC connection for: " + databaseType);
                yield new GenericJdbcConnection(databaseType);
            }
        };
    }

    /**
     * Checks if a database type has specialized connection support.
     *
     * @param databaseType The database type to check
     * @return true if there's a specialized connection class for this type
     */
    public static boolean hasSpecializedConnection(String databaseType) {
        if (databaseType == null) return false;
        String type = databaseType.toLowerCase().trim();
        return switch (type) {
            case "sqlite", "mysql", "mariadb", "postgres", "postgresql",
                 "cockroachdb", "timescaledb" -> true;
            default -> false;
        };
    }
}
