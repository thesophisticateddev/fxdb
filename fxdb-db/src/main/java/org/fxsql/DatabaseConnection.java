package org.fxsql;


import javafx.beans.property.ReadOnlyDoubleProperty;

import java.sql.ResultSet;
import java.util.List;

public interface DatabaseConnection {
    void connect(String connection);
    void disconnect();

    boolean isConnected();

//    void setDownloadProgressBar(ProgressBar pb);

    ReadOnlyDoubleProperty downloadDriverInTheBackground();

    List<String> getTableNames();

    ResultSet executeReadQuery(String sql);
}
