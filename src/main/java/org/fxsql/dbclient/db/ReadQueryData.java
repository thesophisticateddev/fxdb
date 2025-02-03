package org.fxsql.dbclient.db;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReadQueryData {
    private List<String> columnNames;


    public ReadQueryData(ResultSet resultSet) {
        setupColumnNames(resultSet);
    }

    private void setupColumnNames(ResultSet resultSet){
        try {
            columnNames = new ArrayList<>();
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int totalColumns = resultSetMetaData.getColumnCount();
            for (int i = 0; i < totalColumns; i++) {
                columnNames.add(resultSetMetaData.getColumnName(i));
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }



    public List<String> getColumnNames() {
        return columnNames;
    }
}
