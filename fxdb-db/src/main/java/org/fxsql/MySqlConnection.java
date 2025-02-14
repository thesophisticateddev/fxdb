package org.fxsql;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.concurrent.Task;
import javafx.scene.control.ProgressBar;

import java.sql.ResultSet;
import java.util.List;

public class MySqlConnection implements DatabaseConnection{

    private final DynamicJDBCDriverLoader dynamicJDBCDriverLoader = new DynamicJDBCDriverLoader();

    private ProgressBar progressBar;
    @Override
    public void connect(String connectionString) {

    }

    @Override
    public void disconnect() {

    }

    @Override
    public boolean isConnected() {
        return false;
    }

//    @Override
//    public void setDownloadProgressBar(ProgressBar pb) {
//        progressBar = pb;
//    }

    @Override
    public ReadOnlyDoubleProperty downloadDriverInTheBackground() {

        return dynamicJDBCDriverLoader.downloadDriverInTheBackground("mysql");

    }

    @Override
    public List<String> getTableNames() {
        return null;
    }

    @Override
    public ResultSet executeReadQuery(String sql) {
        return null;
    }

    @Override
    public String connectionUrl() {
        return null;
    }
}
