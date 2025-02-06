package org.fxsql.dbclient.db;

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

    @Override
    public void setDownloadProgressBar(ProgressBar pb) {
        progressBar = pb;
    }

    @Override
    public void downloadDriverInTheBackground() {
        Task<Void> downloadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                dynamicJDBCDriverLoader.downloadDriverInTheBackground("mysql",progressBar);
                return null;
            }
        };
        new Thread(downloadTask).start();
    }

    @Override
    public List<String> getTableNames() {
        return null;
    }

    @Override
    public ResultSet executeReadQuery(String sql) {
        return null;
    }
}
