package org.fxsql.dialect;

import org.fxdb.plugin.sdk.db.Dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

public final class DialectDetector {

    private static final Logger logger = Logger.getLogger(DialectDetector.class.getName());

    private DialectDetector() {}

    public static Dialect detect(Connection connection) {
        if (connection == null) return Dialect.UNKNOWN;
        try {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null) return Dialect.UNKNOWN;
            return switch (product.toLowerCase()) {
                case "postgresql" -> Dialect.POSTGRESQL;
                case "mysql"      -> Dialect.MYSQL;
                case "sqlite"     -> Dialect.SQLITE;
                case "duckdb"     -> Dialect.DUCKDB;
                default           -> {
                    logger.warning("Unknown database product name: " + product);
                    yield Dialect.UNKNOWN;
                }
            };
        } catch (SQLException e) {
            return Dialect.UNKNOWN;
        }
    }
}
