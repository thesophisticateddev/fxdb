package org.fxsql;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.concurrent.Task;
import org.fxsql.events.DriverDownloadEvent;
import org.fxsql.events.DriverLoadedEvent;
import org.fxsql.events.EventBus;
import org.fxsql.service.BackgroundJarDownloadService;

import org.fxsql.config.AppPaths;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DynamicJDBCDriverLoader {

    private final static String DYNAMIC_JAR_PATH = AppPaths.getDir("dynamic-jars").getAbsolutePath();
    private static final Logger logger = Logger.getLogger(DynamicJDBCDriverLoader.class.getName());
    private static final Map<String, URLClassLoader> loaderCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, ClassLoader> driverToLoader = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Registers a driver's classloader for TCCL usage.
     * This allows external loaders (like JDBCDriverLoader) to register their drivers
     * so that withDriverTCCL can find the correct classloader.
     *
     * @param driverClassName The fully qualified driver class name (e.g., "org.sqlite.JDBC")
     * @param classLoader The classloader that loaded this driver
     */
    public static void registerDriverClassLoader(String driverClassName, ClassLoader classLoader) {
        driverToLoader.putIfAbsent(driverClassName, classLoader);
        logger.info("Registered driver classloader for: " + driverClassName);
    }

    /**
     * Checks if a driver's classloader is registered for TCCL usage.
     *
     * @param driverClassName The fully qualified driver class name
     * @return true if the driver's classloader is registered
     */
    public static boolean isDriverClassLoaderRegistered(String driverClassName) {
        return driverToLoader.containsKey(driverClassName);
    }

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
            // Compare against the *underlying* driver class, unwrapping any shim.
            // Both this loader's DriverShim and JDBCDriverLoader's JDBCDriverShim are
            // handled so a driver registered by either subsystem is detected here.
            if (unwrap(d).getClass().getName().equals(driverClassName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unwraps a registered driver to the real underlying JDBC driver. Handles both
     * shim types used in this codebase so "already loaded" detection is consistent
     * regardless of which subsystem registered the driver.
     */
    private static Driver unwrap(Driver d) {
        if (d instanceof DriverShim shim) {
            return shim.getDriver();
        }
        if (d instanceof org.fxsql.driverload.JDBCDriverLoader.JDBCDriverShim shim) {
            return shim.driver();
        }
        return d;
    }

    // ---- Filename-independent driver discovery ---------------------------------
    // The dynamic-jars directory holds versioned jars (e.g. "duckdb_jdbc-1.1.3.jar"),
    // so the loader must never depend on a hard-coded jar filename. Instead it builds a
    // classloader over *every* jar in the directory and discovers drivers by class name.

    private static volatile URLClassLoader dynamicJarsLoader;
    private static volatile Set<String> dynamicJarsSnapshot;

    /**
     * Returns a classloader spanning every {@code *.jar} currently in the dynamic-jars
     * directory. Rebuilt only when the set of jars changes (so newly downloaded drivers
     * are picked up mid-session), and shared across jars so inter-jar dependencies resolve.
     */
    private static synchronized URLClassLoader getDynamicJarsClassLoader() {
        File dir = new File(DYNAMIC_JAR_PATH);
        File[] found = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
        List<File> jars = (found == null) ? List.of() : Arrays.asList(found);

        Set<String> snapshot = new TreeSet<>();
        for (File jar : jars) {
            snapshot.add(jar.getName());
        }

        if (dynamicJarsLoader != null && snapshot.equals(dynamicJarsSnapshot)) {
            return dynamicJarsLoader;
        }

        List<URL> urls = new ArrayList<>();
        for (File jar : jars) {
            try {
                urls.add(jar.toURI().toURL());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Skipping unreadable driver jar: " + jar, e);
            }
        }
        dynamicJarsLoader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
        dynamicJarsSnapshot = snapshot;
        return dynamicJarsLoader;
    }

    /**
     * Single source of truth for "is this driver available?". True if the driver is
     * already registered, or if some jar in the dynamic-jars directory provides its
     * class. Independent of jar filenames, so it can never disagree with the loader.
     */
    public static boolean isDriverAvailable(String driverClassName) {
        if (isDriverAlreadyLoaded(driverClassName)) {
            return true;
        }
        String resource = driverClassName.replace('.', '/') + ".class";
        return getDynamicJarsClassLoader().findResource(resource) != null;
    }

    /**
     * Loads and registers the given JDBC driver from whichever jar in the dynamic-jars
     * directory contains it. Idempotent — returns true immediately if already loaded.
     *
     * @return true if the driver is loaded and registered; false if no jar provides it.
     */
    public static synchronized boolean loadDriverByClass(String driverClassName) {
        if (isDriverAlreadyLoaded(driverClassName)) {
            // Ensure the TCCL mapping exists even if another subsystem registered it.
            driverToLoader.putIfAbsent(driverClassName, getDynamicJarsClassLoader());
            return true;
        }
        URLClassLoader ucl = getDynamicJarsClassLoader();
        try {
            Class<?> clazz = Class.forName(driverClassName, true, ucl);
            if (!Driver.class.isAssignableFrom(clazz) || clazz.isInterface()) {
                logger.warning(driverClassName + " is not a concrete JDBC Driver implementation");
                return false;
            }
            Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(driver));
            driverToLoader.put(driverClassName, ucl);
            logger.info("Dynamically loaded and registered driver: " + driverClassName);
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            logger.warning("No jar in " + DYNAMIC_JAR_PATH + " provides driver class " + driverClassName);
            return false;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to instantiate driver " + driverClassName, e);
            return false;
        }
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
        return isDriverAvailable("org.sqlite.JDBC");
    }

    public static boolean loadSQLiteJDBCDriver() throws Exception {
        if (isDriverAlreadyLoaded("org.sqlite.JDBC")) {
            return true;
        }
        boolean loaded = loadDriverByClass("org.sqlite.JDBC");
        if (loaded) {
            EventBus.fireEvent(new DriverLoadedEvent("SQLite driver loaded"));
        }
        return loaded;
    }

    public static boolean loadDuckDBJDBCDriver() throws Exception {
        if (isDriverAlreadyLoaded("org.duckdb.DuckDBDriver")) {
            return true;
        }
        boolean loaded = loadDriverByClass("org.duckdb.DuckDBDriver");
        if (loaded) {
            EventBus.fireEvent(new DriverLoadedEvent("DuckDB driver loaded"));
        }
        return loaded;
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

    public void downloadPostgresqlJDBCDriver(){
        String downloadUrl = "https://jdbc.postgresql.org/download/postgresql-42.7.8.jar";
        try {
            downloadJDBCDriver("postgres-jdbc.jar", downloadUrl);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
