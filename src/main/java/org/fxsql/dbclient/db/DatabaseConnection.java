package org.fxsql.dbclient.db;

import javafx.scene.control.ProgressBar;

public interface DatabaseConnection {
    void connect(String connection);
    void disconnect();

    boolean isConnected();

    void setDownloadProgressBar(ProgressBar pb);

    void downloadDriverInTheBackground();
}
