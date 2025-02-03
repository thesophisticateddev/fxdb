package org.fxsql.dbclient.db;

import javafx.concurrent.Task;
import javafx.scene.control.ProgressBar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DynamicJDBCDriverLoader {

    private final Logger logger = Logger.getLogger(DynamicJDBCDriverLoader.class.getName());

    public  boolean checkJDBCDriverJarExists(String destinationDir, String driverName) {
        Path dynamicJarsPath = Paths.get(destinationDir);
        Path jarFilePath = dynamicJarsPath.resolve(driverName);

        //If jar file already exist then do not download it!
        if (Files.exists(jarFilePath)) {
            System.out.println("SQLite driver already exists");
            return true;
        }
        return false;

    }

    public  void loadAndRegisterJDBCDriver(String driverJarPath, String driverClassName) throws Exception {
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

    public  boolean isSqliteJDBCJarAvailable(){
        return checkJDBCDriverJarExists("dynamic-jars","sqlite-jdbc.jar");
    }
    public  boolean loadSQLiteJDBCDriver() {
        // Fully qualified name of the driver class
//        final String driverClassName = "com.database.jdbc.Driver";
        final String driverClassName = "org.sqlite.JDBC";
        if(!isSqliteJDBCJarAvailable()){
            System.out.println("Sqlite jar not downloaded");
            return false;
        }
        try {

            loadAndRegisterJDBCDriver("dynamic-jars/sqlite-jdbc.jar", driverClassName);
            System.out.println("SQLite driver loaded");
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Could not load the SQL driver", e);
        }
        return false;
    }

    private  void downloadJDBCDriver(String driverName, String downloadUrl, String destinationDir) throws
            IOException {
        // Define the destination path for the JAR file
        Path dynamicJarsPath = Paths.get(destinationDir);
        Path jarFilePath = dynamicJarsPath.resolve(driverName);

        // Create the dynamic-jars directory if it doesn't exist
        if (Files.notExists(dynamicJarsPath)) {
            Files.createDirectories(dynamicJarsPath);
        }
        //Check if driver already exists
        if (checkJDBCDriverJarExists(destinationDir, driverName)) {
            System.out.println("JDBC driver exists in directory");
            return;
        }

        // Download the JAR file
        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, jarFilePath);
            System.out.println("JDBC Driver downloaded successfully to " + jarFilePath);
        }
        catch (IOException e) {
            System.err.println("Failed to download the JDBC Driver: " + e.getMessage());
            throw e;
        }
    }

    public void downloadDriverInTheBackground(String dbType,ProgressBar progressBar){
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if(dbType.equalsIgnoreCase("sqlite")) {
                    downloadSQLiteJDBCDriver();
                }
                return null;
            }
        };

        //bind the progress to the progress bar property
        progressBar.progressProperty().bind(task.progressProperty());
        //Start the task on a new thread
        new Thread(task).start();
    }
    private  void downloadSQLiteJDBCDriver() {
        String downloadUrl = "https://sourceforge.net/projects/sqlite-jdbc-driver.mirror/files/latest/download";
        String destinationDir = "dynamic-jars";
        try {
            downloadJDBCDriver("sqlite-jdbc.jar", downloadUrl, destinationDir);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public  void downloadMySQLJDBCDriver() {
        String downloadUrl = "https://sourceforge.net/projects/sqlite-jdbc-driver.mirror/files/latest/download";
        String destinationDir = "dynamic-jars";
        try {
            downloadJDBCDriver("mysql-jdbc.jar", downloadUrl, destinationDir);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
