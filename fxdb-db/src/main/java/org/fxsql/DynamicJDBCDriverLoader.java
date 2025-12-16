package org.fxsql;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.concurrent.Task;
import org.fxsql.events.DriverDownloadEvent;
import org.fxsql.events.DriverLoadedEvent;
import org.fxsql.events.EventBus;
import org.fxsql.service.BackgroundJarDownloadService;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public void loadAndRegisterJDBCDriverDynamically(String fullDriverJarPath) throws Exception {

        // 1. Setup the ClassLoader
        File file = new File(fullDriverJarPath);
        URL jarUrl = file.toURI().toURL();
        // Use a try-with-resources block for the ClassLoader
        try (URLClassLoader ucl = new URLClassLoader(new URL[]{jarUrl}, this.getClass().getClassLoader())) {

            // 2. Inspect the JAR file
            try (JarFile jarFile = new JarFile(file)) {

                // Loop through all entries in the JAR

                jarFile.stream().forEach(entry -> {
                    String name = entry.getName();

                    // Only look at .class files and exclude module-info
                    if (name.endsWith(".class") && !name.equals("module-info.class")) {

                        // Convert file path style to Java class name style
                        String className = name.replace('/', '.').substring(0, name.length() - 6);

                        try {
                            // 3. Attempt to load the class
                            Class<?> clazz = Class.forName(className, false, ucl);

                            // 4. Check if it implements java.sql.Driver
                            if (Driver.class.isAssignableFrom(clazz) && !clazz.isInterface()) {

                                // 5. Instantiate and Register the Driver
                                @SuppressWarnings("unchecked")
                                Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
                                DriverManager.registerDriver(driver);

                                System.out.println("Dynamically loaded and registered: " + className);

                                // Exit the loop once a driver is found (usually only one per JAR)
                            }
                        } catch (ClassNotFoundException e) {
                            // Expected: Class.forName(..., false, ...) is used to load without initializing
                            // This handles classes that fail to load due to missing dependencies/runtime setup
                        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
                            // Expected: Happens if a dependency is missing or static block fails
                            // We suppress this and continue searching
                            System.err.println("Skipping class " + className + " due to load failure.");
                        } catch (SQLException e) {
                            logger.log(Level.WARNING, "Error while trying to load jdbc driver, SQL Exception occured " + className, e);
//                            throw new RuntimeException(e);
                        } catch (InvocationTargetException | InstantiationException e) {
                            logger.log(Level.WARNING, "Exception occured while trying to load jdbc driver", e);
//                            throw new RuntimeException(e);
                        } catch (IllegalAccessException | NoSuchMethodException e) {
//                            throw new RuntimeException(e);
                            logger.log(Level.WARNING, "Exception occured while trying to load jdbc driver", e);
                        }
                    }
                });

//                throw new Exception("Could not find a valid java.sql.Driver implementation in " + fullDriverJarPath);

            } catch (IOException e) {
                System.err.println("Error reading JAR file: " + fullDriverJarPath);
                throw e;
            }
        }
    }

    public Set<String> listFilesUsingFilesList(String dir) throws IOException {
        try (Stream<Path> stream = Files.list(Paths.get(dir))) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
        }
    }

    public void loadAndRegisterAllJDBCDrivers() throws Exception {
        // Get all jdbc drivers list

        //read every jdbc.jar in folder
        Set<String> jarFiles = listFilesUsingFilesList(DYNAMIC_JAR_PATH);
        for (String jarFile : jarFiles) {
            if (jarFile.endsWith(".jar") && jarFile.contains("jdbc")) {
                loadAndRegisterJDBCDriver(jarFile, "jdbc");
            }
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
        } catch (Exception e) {
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void downloadMySQLJDBCDriver() {
        String downloadUrl = "https://sourceforge.net/projects/sqlite-jdbc-driver.mirror/files/latest/download";
        try {
            downloadJDBCDriver("mysql-jdbc.jar", downloadUrl);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
