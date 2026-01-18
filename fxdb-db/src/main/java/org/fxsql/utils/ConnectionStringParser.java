package org.fxsql.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectionStringParser {
    private String host;
    private int port = -1;
    private String database;
    private String user;
    private String password;

    /**
     * Parses a connection string in various formats:
     * 1. JDBC format: "jdbc:postgresql://host:port/database?user=value&password=value"
     * 2. Key-value format: "host=localhost;port=5432;db=mydb;user=admin;pass=secret"
     * 3. URI format: "postgresql://user:password@host:port/database"
     */
    public void parseConnectionString(String connectionString) throws IllegalArgumentException {
        if (connectionString == null || connectionString.trim().isEmpty()) {
            throw new IllegalArgumentException("Connection string cannot be null or empty");
        }

        connectionString = connectionString.trim();

        // Try to parse as JDBC URL
        if (connectionString.startsWith("jdbc:")) {
            parseJdbcUrl(connectionString);
        }
        // Try to parse as URI format (e.g., postgresql://...)
        else if (connectionString.contains("://")) {
            parseUriFormat(connectionString);
        }
        // Parse as key-value pairs (e.g., host=...;port=...;...)
        else if (connectionString.contains("=")) {
            parseKeyValueFormat(connectionString);
        }
        else {
            throw new IllegalArgumentException("Unrecognized connection string format");
        }

        // Validate that minimum required fields are present
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host is required in connection string");
        }
    }

    private void parseJdbcUrl(String jdbcUrl) {
        try {
            // Remove "jdbc:" prefix
            String uriString = jdbcUrl.substring(5);

            // Handle JDBC-specific formatting
            URI uri = new URI(uriString);

            this.host = uri.getHost();
            this.port = uri.getPort();

            // Extract database from path
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                this.database = path.substring(1); // Remove leading "/"
            }

            // Parse query parameters for user and password
            String query = uri.getQuery();
            if (query != null) {
                Map<String, String> params = parseQueryString(query);
                this.user = params.get("user");
                this.password = params.get("password");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid JDBC URL format: " + e.getMessage(), e);
        }
    }

    private void parseUriFormat(String uriString) {
        try {
            URI uri = new URI(uriString);

            this.host = uri.getHost();
            this.port = uri.getPort();

            // Extract database from path
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                this.database = path.substring(1); // Remove leading "/"
            }

            // Extract user and password from userInfo
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                String[] credentials = userInfo.split(":", 2);
                this.user = credentials[0];
                if (credentials.length > 1) {
                    this.password = credentials[1];
                }
            }

            // Also check query parameters
            String query = uri.getQuery();
            if (query != null) {
                Map<String, String> params = parseQueryString(query);
                if (this.user == null) this.user = params.get("user");
                if (this.password == null) this.password = params.get("password");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI format: " + e.getMessage(), e);
        }
    }

    private void parseKeyValueFormat(String kvString) {
        // Split by semicolon or ampersand
        String[] parts = kvString.split("[;&]");

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            int equalsIndex = part.indexOf('=');
            if (equalsIndex == -1) {
                throw new IllegalArgumentException("Invalid key-value pair: " + part);
            }

            String key = part.substring(0, equalsIndex).trim().toLowerCase();
            String value = part.substring(equalsIndex + 1).trim();

            // Remove quotes if present
            value = value.replaceAll("^['\"]|['\"]$", "");

            switch (key) {
                case "host":
                case "server":
                case "hostname":
                    this.host = value;
                    break;
                case "port":
                    try {
                        this.port = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid port number: " + value, e);
                    }
                    break;
                case "db":
                case "database":
                case "dbname":
                    this.database = value;
                    break;
                case "user":
                case "username":
                case "uid":
                    this.user = value;
                    break;
                case "pass":
                case "password":
                case "pwd":
                    this.password = value;
                    break;
                default:
                    // Ignore unknown keys
                    break;
            }
        }
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = query.split("&");

        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                params.put(key, value);
            }
        }

        return params;
    }

    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUser() { return user; }
    public String getPassword() { return password; }

    @Override
    public String toString() {
        return String.format("Connection{host='%s', port=%d, database='%s', user='%s', password=%s}",
                host, port, database, user, password != null ? "***" : "null");
    }

//    // Example usage
//    public static void main(String[] args) {
//        ConnectionStringParser parser = new ConnectionStringParser();
//
//        // Test different formats
//        String[] testStrings = {
//                "jdbc:postgresql://localhost:5432/mydb?user=admin&password=secret",
//                "host=localhost;port=5432;db=mydb;user=admin;pass=secret",
//                "postgresql://admin:secret@localhost:5432/mydb"
//        };
//
//        for (String connStr : testStrings) {
//            try {
//                parser.parseConnectionString(connStr);
//                System.out.println("Parsed: " + connStr);
//                System.out.println("Result: " + parser);
//                System.out.println();
//            } catch (IllegalArgumentException e) {
//                System.err.println("Error parsing: " + connStr);
//                System.err.println("Message: " + e.getMessage());
//                System.out.println();
//            }
//        }
//  }
}
