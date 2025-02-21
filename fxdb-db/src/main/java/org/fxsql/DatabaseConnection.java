package org.fxsql;


import javafx.beans.property.ReadOnlyDoubleProperty;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface DatabaseConnection {
    void connect(String connection) throws SQLException;
    void disconnect();

    boolean isConnected();

//    void setDownloadProgressBar(ProgressBar pb);

    ReadOnlyDoubleProperty downloadDriverInTheBackground();

    List<String> getTableNames();

    ResultSet executeReadQuery(String sql) throws SQLException;

    String connectionUrl();
}
