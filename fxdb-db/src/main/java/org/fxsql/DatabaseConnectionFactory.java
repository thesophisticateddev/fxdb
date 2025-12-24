package org.fxsql;

public class DatabaseConnectionFactory {

    public static DatabaseConnection getConnection(String databaseType){
        return switch (databaseType) {
            case "sqlite" -> new SqliteConnection();
            case "mysql" -> new MySqlConnection();
            case "postgres" -> new PostgresSqlConnection();
            default -> throw new IllegalArgumentException("Unsupported database type");
        };

    }
}
