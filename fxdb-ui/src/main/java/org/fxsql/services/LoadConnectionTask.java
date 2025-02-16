package org.fxsql.services;

import javafx.concurrent.Task;
import org.fxsql.ConnectionMetaData;
import org.fxsql.DatabaseConnection;
import org.fxsql.DatabaseConnectionFactory;
import org.fxsql.DatabaseManager;

public class LoadConnectionTask extends Task<Void> {
    private DatabaseManager databaseManager;
    private String connectionName;

    @Override
    protected Void call() throws Exception {
        return null;
    }

   public LoadConnectionTask(DatabaseManager dm, String connectionName){
        super();
        this.databaseManager = dm;
        this.connectionName = connectionName;

   }


}
