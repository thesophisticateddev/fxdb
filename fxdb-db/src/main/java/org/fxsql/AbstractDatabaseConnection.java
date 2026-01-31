package org.fxsql;

import com.google.inject.Inject;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.driverload.JDBCDriverLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractDatabaseConnection implements DatabaseConnection {

    public AbstractDatabaseConnection(){

    }
    protected Map<String, Object> metaData = new ConcurrentHashMap<>();

    // Assuming DynamicJDBCDriverLoader handles downloading and loading the driver JAR
    protected final DynamicJDBCDriverLoader dynamicJDBCDriverLoader = new DynamicJDBCDriverLoader();
    protected Connection connection;

    @Inject
    private DriverDownloader driverDownloader;


    public void setUserName(String username){
        metaData.put("username",username);
    }

    public void setPassword(String password){
        metaData.put("password",password);
    }


    protected String getPassword(){
        return this.metaData.get("password").toString();
    }

    protected String getUserName(){
        return this.metaData.get("username").toString();
    }
    public boolean isWriteQuery(String sql) {
        String trimmed = sql.trim().toUpperCase();
        return trimmed.startsWith("INSERT") ||
                trimmed.startsWith("UPDATE") ||
                trimmed.startsWith("DELETE") ||
                trimmed.startsWith("CREATE") ||
                trimmed.startsWith("DROP") ||
                trimmed.startsWith("ALTER") ||
                trimmed.startsWith("TRUNCATE") ||
                trimmed.startsWith("MERGE");
    }


    public boolean isDriverLoaded(String className){
        var drivers= DriverManager.getDrivers().asIterator();
        while (drivers.hasNext()) {
            JDBCDriverLoader.JDBCDriverShim  d = (JDBCDriverLoader.JDBCDriverShim) drivers.next();
            if(d.driver().getClass().getName().contains(className)){
                return true;
            }
        }
        return false;
    }
}
