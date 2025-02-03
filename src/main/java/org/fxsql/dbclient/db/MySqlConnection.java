package org.fxsql.dbclient.db;

import javafx.scene.control.ProgressBar;

public class MySqlConnection implements DatabaseConnection{
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

    }
}
