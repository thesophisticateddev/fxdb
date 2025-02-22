package org.fxsql.fxdb.databaseManagement;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.concurrent.Task;
import org.fxsql.fxdb.services.core.events.DriverDownloadEvent;
import org.fxsql.fxdb.services.core.events.DriverLoadedEvent;
import org.fxsql.fxdb.services.core.events.EventBus;
import org.fxsql.fxdb.services.core.service.BackgroundJarDownloadService;


import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DynamicJDBCDriverLoader {

    private final static String DYNAMIC_JAR_PATH = "dynamic-jars";
    private final Logger logger = Logger.getLogger(DynamicJDBCDriverLoader.class.getName());

    public void loadAndRegisterJDBCDriver(String driverJarPath, String driverClassName) throws Exception {
        // Create a URLClassLoader with the path to the driver JAR
        URL jarUrl = new URL("jar:file:" + driverJarPath + "!/");
        try {
            URLClassLoader ucl = new URLClassLoader(new URL[]{jarUrl});
            Driver driver = (Driver) Class.forName(driverClassName, true, ucl).getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(driver));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    public boolean isSqliteJDBCJarAvailable() {
//        return checkJDBCDriverJarExists(DYNAMIC_JAR_PATH,"sqlite-jdbc.jar");
        return BackgroundJarDownloadService.checkJarAlreadyExists(DYNAMIC_JAR_PATH, "sqlite-jdbc.jar");
    }

    public boolean loadSQLiteJDBCDriver() {
        // Fully qualified name of the driver class
//        final String driverClassName = "com.database.jdbc.Driver";
        final String driverClassName = "org.sqlite.JDBC";
        if (!isSqliteJDBCJarAvailable()) {
            System.out.println("Sqlite jar not downloaded");
            return false;
        }
        try {

            loadAndRegisterJDBCDriver(DYNAMIC_JAR_PATH + "/sqlite-jdbc.jar", driverClassName);
            System.out.println("SQLite driver loaded");
            EventBus.fireEvent(new DriverLoadedEvent("SQLite driver loaded"));
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Could not load the SQL driver", e);
        }
        return false;
    }

    private void downloadJDBCDriver(String driverName, String downloadUrl) throws IOException {
        BackgroundJarDownloadService.downloadJarFile(DYNAMIC_JAR_PATH, driverName, downloadUrl);
    }

    public ReadOnlyDoubleProperty downloadDriverInTheBackground(String dbType) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (dbType.equalsIgnoreCase("sqlite")) {
                    downloadSQLiteJDBCDriver();
                }
                return null;
            }
        };

//        //bind the progress to the progress bar property
//        progressBar.progressProperty().bind(task.progressProperty());
        //Start the task on a new thread
        new Thread(task).start();
        return task.progressProperty();
    }

    private void downloadSQLiteJDBCDriver() {
        String downloadUrl = "https://sourceforge.net/projects/sqlite-jdbc-driver.mirror/files/latest/download";
        try {
            downloadJDBCDriver("sqlite-jdbc.jar", downloadUrl);
            EventBus.fireEvent(new DriverDownloadEvent("SQLite driver downloaded successfully!"));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void downloadMySQLJDBCDriver() {
        String downloadUrl = "https://sourceforge.net/projects/sqlite-jdbc-driver.mirror/files/latest/download";
        try {
            downloadJDBCDriver("mysql-jdbc.jar", downloadUrl);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
