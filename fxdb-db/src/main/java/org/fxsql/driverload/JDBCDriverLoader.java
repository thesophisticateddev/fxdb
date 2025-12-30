package org.fxsql.driverload;

import javafx.application.Platform;
import javafx.concurrent.Task;

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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JDBCDriverLoader {

    private static final Logger logger = Logger.getLogger(JDBCDriverLoader.class.getName());
    private static final String DEFAULT_DRIVERS_DIR = "dynamic-jars";
    private final Set<Driver> loadedDrivers = Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JDBC-Driver-Loader");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, URLClassLoader> loaderCache = new java.util.concurrent.ConcurrentHashMap<>();

    private URLClassLoader getOrCreateLoader(String fullDriverJarPath) {
        String key;
        try {
            key = Paths.get(fullDriverJarPath).toAbsolutePath().normalize().toFile().getCanonicalPath();
        } catch (IOException e) {
            key = Paths.get(fullDriverJarPath).toAbsolutePath().normalize().toString();
        }

        return loaderCache.computeIfAbsent(key, k -> {
            try {
                // Use system/app classloader as parent (NOT plugin CL)
                return new URLClassLoader(new URL[]{ new File(k).toURI().toURL() }, ClassLoader.getSystemClassLoader());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }


    /**
     * Loads all JDBC drivers asynchronously in a background thread.
     * This prevents UI freezing during driver loading.
     *
     * @param driversDirectory Path to the directory containing JDBC driver JAR files
     * @param onComplete Callback invoked on JavaFX thread when loading completes
     * @param onProgress Optional callback for progress updates (can be null)
     */
    public void loadAllDriversOnStartupAsync(String driversDirectory,
                                             Consumer<DriverLoadResult> onComplete,
                                             Consumer<DriverLoadProgress> onProgress) {

        Task<DriverLoadResult> loadTask = new Task<>() {
            @Override
            protected DriverLoadResult call() throws Exception {
                logger.info("Starting async JDBC driver loading process...");
                updateMessage("Initializing driver loading...");

                // Validate directory
                Path driverPath = Paths.get(driversDirectory);
                if (!Files.exists(driverPath)) {
                    logger.info("Drivers directory does not exist: " + driversDirectory);
                    return new DriverLoadResult(0, 0, "Directory does not exist");
                }

                if (!Files.isDirectory(driverPath)) {
                    logger.warning("Path exists but is not a directory: " + driversDirectory);
                    return new DriverLoadResult(0, 0, "Path is not a directory");
                }

                // Get all JAR files
                Set<String> jarFiles;
                try {
                    updateMessage("Scanning for JAR files...");
                    jarFiles = listJarFiles(driversDirectory);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed to list files in drivers directory", e);
                    return new DriverLoadResult(0, 0, "Failed to scan directory: " + e.getMessage());
                }

                if (jarFiles.isEmpty()) {
                    logger.info("No JAR files found in drivers directory");
                    return new DriverLoadResult(0, 0, "No JAR files found");
                }

                logger.info("Found " + jarFiles.size() + " JAR file(s) in drivers directory");

                // Load drivers from each JAR
                int successCount = 0;
                int failureCount = 0;
                int currentIndex = 0;
                int totalJars = jarFiles.size();
                List<String> loadedDriverNames = new ArrayList<>();
                List<String> failedJars = new ArrayList<>();

                for (String jarFileName : jarFiles) {
                    currentIndex++;
                    updateMessage("Processing " + jarFileName + " (" + currentIndex + "/" + totalJars + ")");
                    updateProgress(currentIndex, totalJars);

                    // Notify progress if callback provided
                    if (onProgress != null) {
                        int current = currentIndex;
                        Platform.runLater(() ->
                                onProgress.accept(new DriverLoadProgress(current, totalJars, jarFileName))
                        );
                    }

                    String fullPath = Paths.get(driversDirectory, jarFileName).toString();
                    try {
                        logger.info("Processing JAR: " + jarFileName);
                        LoadResult result = loadAndRegisterJDBCDriverDynamically(fullPath);

                        if (result.success) {
                            successCount++;
                            loadedDriverNames.addAll(result.driverNames);
                        } else {
                            failureCount++;
                            failedJars.add(jarFileName);
                        }

                        // Small delay to prevent overwhelming the system
                        Thread.sleep(50);

                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to load driver from: " + jarFileName, e);
                        failureCount++;
                        failedJars.add(jarFileName + " (" + e.getMessage() + ")");
                    }
                }

                logger.info(String.format("Driver loading complete: %d successful, %d failed, %d total",
                        successCount, failureCount, totalJars));

                return new DriverLoadResult(successCount, failureCount,
                        "Completed", loadedDriverNames, failedJars);
            }
        };

        loadTask.setOnSucceeded(event -> {
            DriverLoadResult result = loadTask.getValue();
            logger.info("Driver loading task completed successfully");
            onComplete.accept(result);
        });

        loadTask.setOnFailed(event -> {
            Throwable e = loadTask.getException();
            logger.log(Level.SEVERE, "Driver loading task failed", e);
            DriverLoadResult result = new DriverLoadResult(0, 0,
                    "Fatal error: " + (e != null ? e.getMessage() : "Unknown error"));
            onComplete.accept(result);
        });

        executorService.submit(loadTask);
    }

    /**
     * Convenience method using default directory with callback
     */
    public void loadAllDriversOnStartupAsync(Consumer<DriverLoadResult> onComplete) {
        loadAllDriversOnStartupAsync(DEFAULT_DRIVERS_DIR, onComplete, null);
    }

    /**
     * Convenience method with progress callback
     */
    public void loadAllDriversOnStartupAsync(Consumer<DriverLoadResult> onComplete,
                                             Consumer<DriverLoadProgress> onProgress) {
        loadAllDriversOnStartupAsync(DEFAULT_DRIVERS_DIR, onComplete, onProgress);
    }

    /**
     * Synchronous version for compatibility (runs on current thread)
     */
    public int loadAllDriversOnStartup(String driversDirectory) {
        logger.info("Starting synchronous JDBC driver loading process...");

        Path driverPath = Paths.get(driversDirectory);
        if (!Files.exists(driverPath) || !Files.isDirectory(driverPath)) {
            logger.info("Drivers directory does not exist or is not a directory: " + driversDirectory);
            return 0;
        }

        Set<String> jarFiles;
        try {
            jarFiles = listJarFiles(driversDirectory);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to list files in drivers directory", e);
            return 0;
        }

        if (jarFiles.isEmpty()) {
            logger.info("No JAR files found in drivers directory");
            return 0;
        }

        int successCount = 0;
        for (String jarFileName : jarFiles) {
            String fullPath = Paths.get(driversDirectory, jarFileName).toString();
            try {
                LoadResult result = loadAndRegisterJDBCDriverDynamically(fullPath);
                if (result.success) {
                    successCount++;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load driver from: " + jarFileName, e);
            }
        }

        logger.info("Loaded " + successCount + " driver(s) successfully");
        return successCount;
    }

    /**
     * Lists all JAR files in the specified directory (non-recursive)
     */
    private Set<String> listJarFiles(String dir) throws IOException {
        try (Stream<Path> stream = Files.list(Paths.get(dir))) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .filter(file -> file.toString().toLowerCase().endsWith(".jar"))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Internal result class for single JAR loading
     */
    private static class LoadResult {
        boolean success;
        List<String> driverNames = new ArrayList<>();
    }

    /**
     * Loads and registers JDBC drivers from a JAR file
     */
    private LoadResult loadAndRegisterJDBCDriverDynamically(String fullDriverJarPath) throws Exception {
        LoadResult result = new LoadResult();
        File file = new File(fullDriverJarPath);

        if (!file.exists() || !file.canRead()) return result;

        URLClassLoader ucl = getOrCreateLoader(fullDriverJarPath);

        try (JarFile jarFile = new JarFile(file)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.endsWith(".class") || name.equals("module-info.class")) continue;

                String className = name.replace('/', '.').substring(0, name.length() - 6);

                try {
                    Class<?> clazz = Class.forName(className, false, ucl);
                    if (Driver.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();

                        // register shim that sets TCCL
                        DriverManager.registerDriver(new DriverShim(driver, ucl));

                        loadedDrivers.add(driver); // (see Fix 3 below)
                        result.success = true;
                        result.driverNames.add(className);

                        logger.info("Successfully loaded driver: " + className);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return result;
    }

    /**
     * Gets information about all currently loaded drivers
     */
    public List<DriverInfo> getLoadedDriversInfo() {
        List<DriverInfo> drivers = new ArrayList<>();
        Enumeration<Driver> driverEnum = DriverManager.getDrivers();

        while (driverEnum.hasMoreElements()) {
            Driver driver = driverEnum.nextElement();
            drivers.add(new DriverInfo(
                    driver.getClass().getName(),
                    driver.getMajorVersion(),
                    driver.getMinorVersion(),
                    driver.jdbcCompliant()
            ));
        }

        return drivers;
    }

    /**
     * Prints loaded drivers information
     */
    public void printLoadedDrivers() {
        List<DriverInfo> drivers = getLoadedDriversInfo();

        if (drivers.isEmpty()) {
            System.out.println("No JDBC drivers are currently loaded.");
            return;
        }

        System.out.println("\n=== Loaded JDBC Drivers ===");
        for (DriverInfo info : drivers) {
            System.out.println(info);
        }
        System.out.println("===========================\n");
    }

    /**
     * Unregisters all dynamically loaded drivers
     */
    public void unregisterAllDrivers() {
        logger.info("Unregistering all dynamically loaded drivers...");

        for (Driver driver : loadedDrivers) {
            try {
                DriverManager.deregisterDriver(driver);
                logger.info("Deregistered driver: " + driver.getClass().getName());
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to deregister driver", e);
            }
        }

        loadedDrivers.clear();
    }

    /**
     * Shuts down the executor service
     */
    public void shutdown() {
        executorService.shutdown();
    }

    // ========== Helper Classes ==========

    /**
     * Result of driver loading operation
     */
    public static class DriverLoadResult {
        public final int successCount;
        public final int failureCount;
        public final String message;
        public final List<String> loadedDrivers;
        public final List<String> failedJars;

        public DriverLoadResult(int successCount, int failureCount, String message) {
            this(successCount, failureCount, message, new ArrayList<>(), new ArrayList<>());
        }

        public DriverLoadResult(int successCount, int failureCount, String message,
                                List<String> loadedDrivers, List<String> failedJars) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.message = message;
            this.loadedDrivers = loadedDrivers;
            this.failedJars = failedJars;
        }

        public boolean isSuccess() {
            return successCount > 0;
        }

        @Override
        public String toString() {
            return String.format("DriverLoadResult{success=%d, failed=%d, message='%s'}",
                    successCount, failureCount, message);
        }
    }

    /**
     * Progress update for driver loading
     */
    public static class DriverLoadProgress {
        public final int current;
        public final int total;
        public final String currentFile;

        public DriverLoadProgress(int current, int total, String currentFile) {
            this.current = current;
            this.total = total;
            this.currentFile = currentFile;
        }

        public double getPercentage() {
            return total > 0 ? (double) current / total * 100.0 : 0.0;
        }
    }

    /**
     * Driver wrapper to handle ClassLoader issues
     */
    private static class DriverShim implements Driver {
        private final Driver driver;
        private final URLClassLoader classLoader;

        DriverShim(Driver driver, URLClassLoader classLoader) {
            this.driver = driver;
            this.classLoader = classLoader;
        }

        @Override
        public java.sql.Connection connect(String url, Properties info) throws SQLException {
            return driver.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return driver.acceptsURL(url);
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return driver.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return driver.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return driver.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return driver.getParentLogger();
        }
    }

    /**
     * Driver information class
     */
    public static class DriverInfo {
        public final String className;
        public final int majorVersion;
        public final int minorVersion;
        public final boolean jdbcCompliant;

        public DriverInfo(String className, int majorVersion, int minorVersion, boolean jdbcCompliant) {
            this.className = className;
            this.majorVersion = majorVersion;
            this.minorVersion = minorVersion;
            this.jdbcCompliant = jdbcCompliant;
        }

        @Override
        public String toString() {
            return String.format("Driver: %s (v%d.%d, JDBC Compliant: %s)",
                    className, majorVersion, minorVersion, jdbcCompliant);
        }
    }
}
