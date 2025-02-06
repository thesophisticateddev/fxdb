package org.fxsql.dbclient.db;

import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SqliteConnection implements DatabaseConnection {

    private static final int ROW_LIMIT = 200;
    private final DynamicJDBCDriverLoader dynamicJDBCDriverLoader = new DynamicJDBCDriverLoader();
    private Connection connection;
    private ProgressBar progressBar;

    public SqliteConnection() {

    }

    public void setDownloadProgressBar(ProgressBar pb) {
        progressBar = pb;
    }

    public void downloadDriverInTheBackground() {
        dynamicJDBCDriverLoader.downloadDriverInTheBackground("sqlite", progressBar);
    }

    @Override
    public List<String> getTableNames() {
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'");

            List<String> tableNames = new ArrayList<>();
            while (rs.next()) {
                tableNames.add(rs.getString("name"));
            }
            return tableNames;
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ResultSet executeReadQuery(String sql) {
        try{
            Statement stmt = connection.createStatement();
            stmt.setMaxRows(ROW_LIMIT);
            return stmt.executeQuery(sql);
        }catch (SQLException e){
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public void connect(String connectionString) {
        try {
//            DynamicJDBCDriverLoader.downloadSQLiteJDBCDriver();
            boolean isAvailable = dynamicJDBCDriverLoader.loadSQLiteJDBCDriver();
            if (!isAvailable) {
                throw new RuntimeException("Driver is not downloaded");
            }
            connection = DriverManager.getConnection(connectionString);
            System.out.println("Connected to SQLite database.");
        }
        catch (SQLException e) {
            System.err.println("Failed to connect to SQLite database: " + e.getMessage());
        }
        catch (RuntimeException e) {
            showInstallDriverAlert(e);
//            dynamicJDBCDriverLoader.downloadSQLiteJDBCDriver();
            downloadDriverInTheBackground();
        }
    }

    private void initializeDatabase() {
        try {
            if (isConnected()) {
                Statement stmt = connection.createStatement();
                stmt.execute("CREATE TABLE IF NOT EXISTS base (id INTEGER PRIMARY, status Text)");
                System.out.println("Created table to database for Base");
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showInstallDriverAlert(Exception exception) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Driver missing");
        alert.setHeaderText("Install the driver");
        alert.setContentText("The driver for jdbc sqlite is missing, if you want to install it just click Ok");

        var stringWriter = new StringWriter();
        var printWriter = new PrintWriter(stringWriter);
        exception.printStackTrace(printWriter);

        TextArea textArea = new TextArea(stringWriter.toString());
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane content = new GridPane();
        content.setMaxWidth(Double.MAX_VALUE);
        content.add(new Label("Full stacktrace:"), 0, 0);
        content.add(textArea, 0, 1);

        alert.getDialogPane().setExpandableContent(content);
        alert.showAndWait();
    }

    @Override
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Disconnected from SQLite database.");
            }
            catch (SQLException e) {
                System.err.println("Failed to disconnect from SQLite database: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        }
        catch (SQLException e) {
            System.err.println("Error checking connection status: " + e.getMessage());
            return false;
        }
    }

    public void executeQuery(String sql) {
        if (connection == null) {
            System.err.println("No active database connection.");
            return;
        }

        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            rs.setFetchSize(500); //Set maximum of 1000 rows to be fetched
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();


            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(rs.getString(i) + "\t");
                }
                System.out.println();
            }
        }
        catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    public void executeUpdate(String sql) {
        if (connection == null) {
            System.err.println("No active database connection.");
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            int rowsAffected = stmt.executeUpdate(sql);
            System.out.println("Query executed successfully. Rows affected: " + rowsAffected);
        }
        catch (SQLException e) {
            System.err.println("Update execution failed: " + e.getMessage());
        }
    }
}
