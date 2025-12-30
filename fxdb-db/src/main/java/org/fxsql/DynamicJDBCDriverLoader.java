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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DynamicJDBCDriverLoader {

    private final static String DYNAMIC_JAR_PATH = "dynamic-jars";
    private final Logger logger = Logger.getLogger(DynamicJDBCDriverLoader.class.getName());
    private static final Map<String, URLClassLoader> loaderCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, ClassLoader> driverToLoader = new java.util.concurrent.ConcurrentHashMap<>();

    private static String canonicalKey(String jarPath) {
        try {
            return Paths.get(jarPath).toAbsolutePath().normalize().toFile().getCanonicalPath();
        } catch (IOException e) {
            return Paths.get(jarPath).toAbsolutePath().normalize().toString();
        }
    }


//    public static void loadAndRegisterJDBCDriver(String driverJarPath, String driverClassName) throws Exception {
//        // Create a URLClassLoader with the path to the driver JAR
//        URL jarUrl = new URL("jar:file:" + driverJarPath + "!/");
//        try {
//            URLClassLoader ucl = new URLClassLoader(new URL[]{jarUrl});
//            Driver driver = (Driver) Class.forName(driverClassName, true, ucl).getDeclaredConstructor().newInstance();
//            DriverManager.registerDriver(new DriverShim(driver));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//    }

    public static boolean isDriverAlreadyLoaded(String driverClassName) {
        var listOfDrivers = DriverManager.getDrivers().asIterator();
        while (listOfDrivers.hasNext()) {
            Driver d = listOfDrivers.next();
            // If it's a DriverShim, we check the underlying driver class name
            if (d instanceof DriverShim) {
                Driver internal = ((DriverShim) d).getDriver();
                if (internal.getClass().getName().equals(driverClassName)) {
                    return true;
                }
            }
            // Direct check for standard drivers
            if (d.getClass().getName().contains(driverClassName)) {
                return true;
            }
        }
        return false;
    }


    public void loadAndRegisterJDBCDriverDynamically(String fullDriverJarPath) throws Exception {
        String key = canonicalKey(fullDriverJarPath);

        // Reuse cached loader and DO NOT close it
        URLClassLoader ucl = loaderCache.computeIfAbsent(key, k -> {
            try {
                return new URLClassLoader(new URL[]{new File(k).toURI().toURL()}, ClassLoader.getSystemClassLoader());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        File file = new File(key);
        try (JarFile jarFile = new JarFile(file)) {
            jarFile.stream().forEach(entry -> {
                String name = entry.getName();
                if (name.endsWith(".class") && !name.equals("module-info.class")) {
                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                    try {
                        Class<?> clazz = Class.forName(className, false, ucl);

                        if (Driver.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                            Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();

                            // IMPORTANT: always register shim
                            DriverManager.registerDriver(new DriverShim(driver));

                            // Remember loader for TCCL usage
                            driverToLoader.putIfAbsent(clazz.getName(), ucl);

                            System.out.println("Dynamically loaded and registered: " + clazz.getName());
                        }
                    } catch (Throwable ignored) {
                        // keep scanning
                    }
                }
            });
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

    public static boolean isSqliteJDBCJarAvailable() {
//        return checkJDBCDriverJarExists(DYNAMIC_JAR_PATH,"sqlite-jdbc.jar");
        return BackgroundJarDownloadService.checkJarAlreadyExists(DYNAMIC_JAR_PATH, "sqlite-jdbc.jar");
    }

    public static boolean loadSQLiteJDBCDriver() throws Exception {
        final String driverClassName = "org.sqlite.JDBC";

        // Check if already loaded to prevent UnsatisfiedLinkError
        if (isDriverAlreadyLoaded(driverClassName)) {
            System.out.println("SQLite driver already loaded and registered. Skipping.");
            return true;
        }

        if (!isSqliteJDBCJarAvailable()) {
            System.out.println("Sqlite jar not downloaded");
            return false;
        }


        loadAndRegisterJDBCDriver(DYNAMIC_JAR_PATH + "/sqlite-jdbc.jar", driverClassName);
        System.out.println("SQLite driver loaded successfully for the first time.");
        EventBus.fireEvent(new DriverLoadedEvent("SQLite driver loaded"));
        return true;

    }

    public static <T> T withDriverTCCL(String driverClassName, java.util.concurrent.Callable<T> action) throws Exception {
        ClassLoader cl = driverToLoader.get(driverClassName);
        if (cl == null) return action.call();
        System.out.println("class loader found"+ cl.getName());
        Thread t = Thread.currentThread();
        System.out.println("Current thread id: " + t.getId());
        ClassLoader prev = t.getContextClassLoader();
        t.setContextClassLoader(cl);
        try {
            return action.call();
        } finally {
            t.setContextClassLoader(prev);
        }
    }

    public static void loadAndRegisterJDBCDriver(String driverJarPath, String driverClassName) throws Exception {
        if (isDriverAlreadyLoaded(driverClassName)) return;

        String key = canonicalKey(driverJarPath);

        URLClassLoader ucl = loaderCache.computeIfAbsent(key, k -> {
            try {
                // parent = system/app classloader
                return new URLClassLoader(new URL[]{new File(k).toURI().toURL()}, ClassLoader.getSystemClassLoader());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        if(driverToLoader.get(driverClassName) != null){
            System.out.println("Driver is already loaded");
            return;
        }
        Driver driver = (Driver) Class.forName(driverClassName, true, ucl)
                .getDeclaredConstructor().newInstance();



        DriverManager.registerDriver(new DriverShim(driver));

        // Remember which loader "owns" this driver
        driverToLoader.putIfAbsent(driverClassName, ucl);
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
