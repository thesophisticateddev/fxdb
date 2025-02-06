package org.fxsql.dbclient.db;

import javafx.scene.control.ProgressBar;

import java.sql.ResultSet;
import java.util.List;

public interface DatabaseConnection {
    void connect(String connection);
    void disconnect();

    boolean isConnected();

    void setDownloadProgressBar(ProgressBar pb);

    void downloadDriverInTheBackground();

    List<String> getTableNames();

    ResultSet executeReadQuery(String sql);
}
