package org.fxsql.driverload;

import com.google.inject.Singleton;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class JDBCDriverLoader {

    private static final Logger logger = Logger.getLogger(JDBCDriverLoader.class.getName());
    private static final String DEFAULT_DRIVERS_DIR = "dynamic-jars";
    private final Set<Driver> loadedDrivers = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String> loadedJarFiles = Collections.synchronizedMap(new HashMap<>());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JDBC-Driver-Loader");
        t.setDaemon(true);
        return t;
    });

    /**
     * Loads all JDBC drivers asynchronously in a background thread.
     * This prevents UI freezing during driver loading.
     *
     * @param driversDirectory Path to the directory containing JDBC driver JAR files
     * @param onComplete       Callback invoked on JavaFX thread when loading completes
     * @param onProgress       Optional callback for progress updates (can be null)
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

        if (!file.exists() || !file.canRead()) {
            logger.warning("Cannot access JAR file: " + fullDriverJarPath);
            return result;
        }

        URL jarUrl = file.toURI().toURL();

        try (URLClassLoader ucl = new URLClassLoader(new URL[]{jarUrl}, this.getClass().getClassLoader());
             JarFile jarFile = new JarFile(file)) {

            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.endsWith(".class") || name.equals("module-info.class")) {
                    continue;
                }

                String className = name.replace('/', '.').substring(0, name.length() - 6);

                try {
                    Class<?> clazz = Class.forName(className, false, ucl);

                    if (Driver.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
                        DriverManager.registerDriver(new DriverShim(driver, ucl));

                        loadedDrivers.add(driver);
                        result.success = true;
                        result.driverNames.add(className);

                        logger.info("Successfully loaded driver: " + className);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError | ExceptionInInitializerError e) {
                    // Expected - silently ignore
                    logger.warning("Class not found for: " + e.getMessage());
                } catch (Exception e) {
                    logger.fine("Cannot load class " + className + ": " + e.getMessage());
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

    /**
     * Checks for new JDBC drivers in the specified directory that haven't been loaded yet.
     * This method scans the directory and compares with already loaded drivers.
     *
     * @param driversDirectory Path to the directory containing JDBC driver JAR files
     * @return NewDriversCheckResult containing information about new drivers found
     */
    public NewDriversCheckResult checkForNewDrivers(String driversDirectory) {
        logger.info("Checking for new JDBC drivers in: " + driversDirectory);

        Path driverPath = Paths.get(driversDirectory);
        if (!Files.exists(driverPath) || !Files.isDirectory(driverPath)) {
            return new NewDriversCheckResult(0, new ArrayList<>(), "Directory does not exist or is not accessible");
        }

        try {
            Set<String> currentJarFiles = listJarFiles(driversDirectory);
            Set<String> loadedJarNames = new HashSet<>(loadedJarFiles.keySet());

            // Find new JARs that haven't been loaded
            Set<String> newJars = currentJarFiles.stream()
                    .filter(jar -> !loadedJarNames.contains(jar))
                    .collect(Collectors.toSet());

            if (newJars.isEmpty()) {
                logger.info("No new drivers found");
                return new NewDriversCheckResult(0, new ArrayList<>(), "No new drivers found");
            }

            logger.info("Found " + newJars.size() + " new driver JAR(s): " + newJars);
            return new NewDriversCheckResult(newJars.size(), new ArrayList<>(newJars),
                    "Found " + newJars.size() + " new driver(s)");

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to check for new drivers", e);
            return new NewDriversCheckResult(0, new ArrayList<>(),
                    "Error scanning directory: " + e.getMessage());
        }
    }

    /**
     * Checks for new drivers asynchronously and notifies via callback
     *
     * @param driversDirectory Path to the directory containing JDBC driver JAR files
     * @param onComplete       Callback invoked with check results
     */
    public void checkForNewDriversAsync(String driversDirectory, Consumer<NewDriversCheckResult> onComplete) {
        Task<NewDriversCheckResult> checkTask = new Task<>() {
            @Override
            protected NewDriversCheckResult call() {
                return checkForNewDrivers(driversDirectory);
            }
        };

        checkTask.setOnSucceeded(event -> onComplete.accept(checkTask.getValue()));
        checkTask.setOnFailed(event -> {
            Throwable e = checkTask.getException();
            logger.log(Level.SEVERE, "Failed to check for new drivers", e);
            onComplete.accept(new NewDriversCheckResult(0, new ArrayList<>(),
                    "Error: " + (e != null ? e.getMessage() : "Unknown error")));
        });

        executorService.submit(checkTask);
    }

    /**
     * Loads only the new drivers that haven't been loaded yet.
     * This is useful for hot-reloading when new driver JARs are added.
     *
     * @param driversDirectory Path to the directory containing JDBC driver JAR files
     * @param onComplete       Callback invoked when loading completes
     * @param onProgress       Optional callback for progress updates
     */
    public void loadNewDriversAsync(String driversDirectory,
                                    Consumer<DriverLoadResult> onComplete,
                                    Consumer<DriverLoadProgress> onProgress) {

        Task<DriverLoadResult> loadTask = new Task<>() {
            @Override
            protected DriverLoadResult call() throws Exception {
                logger.info("Checking for and loading new JDBC drivers...");
                updateMessage("Scanning for new drivers...");

                NewDriversCheckResult checkResult = checkForNewDrivers(driversDirectory);

                if (checkResult.newDriverCount == 0) {
                    logger.info("No new drivers to load");
                    return new DriverLoadResult(0, 0, "No new drivers found");
                }

                logger.info("Loading " + checkResult.newDriverCount + " new driver(s)");

                int successCount = 0;
                int failureCount = 0;
                int currentIndex = 0;
                int totalJars = checkResult.newDriverJars.size();
                List<String> loadedDriverNames = new ArrayList<>();
                List<String> failedJars = new ArrayList<>();

                for (String jarFileName : checkResult.newDriverJars) {
                    currentIndex++;
                    updateMessage("Loading new driver: " + jarFileName + " (" + currentIndex + "/" + totalJars + ")");
                    updateProgress(currentIndex, totalJars);

                    if (onProgress != null) {
                        int current = currentIndex;
                        Platform.runLater(() ->
                                onProgress.accept(new DriverLoadProgress(current, totalJars, jarFileName))
                        );
                    }

                    String fullPath = Paths.get(driversDirectory, jarFileName).toString();
                    try {
                        LoadResult result = loadAndRegisterJDBCDriverDynamically(fullPath);

                        if (result.success) {
                            successCount++;
                            loadedDriverNames.addAll(result.driverNames);
                        } else {
                            failureCount++;
                            failedJars.add(jarFileName);
                        }

                        Thread.sleep(50);

                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to load new driver from: " + jarFileName, e);
                        failureCount++;
                        failedJars.add(jarFileName + " (" + e.getMessage() + ")");
                    }
                }

                logger.info(String.format("New driver loading complete: %d successful, %d failed",
                        successCount, failureCount));

                return new DriverLoadResult(successCount, failureCount,
                        "Loaded " + successCount + " new driver(s)", loadedDriverNames, failedJars);
            }
        };

        loadTask.setOnSucceeded(event -> onComplete.accept(loadTask.getValue()));
        loadTask.setOnFailed(event -> {
            Throwable e = loadTask.getException();
            logger.log(Level.SEVERE, "Failed to load new drivers", e);
            onComplete.accept(new DriverLoadResult(0, 0,
                    "Error: " + (e != null ? e.getMessage() : "Unknown error")));
        });

        executorService.submit(loadTask);
    }

    /**
     * Reloads all currently loaded drivers by unregistering and re-registering them.
     * This is useful for refreshing driver configurations or updating drivers.
     * Note: This does NOT reload the JAR files from disk, it only re-registers existing drivers.
     *
     * @param onComplete Callback invoked when reloading completes
     */
    public void reloadAllDriversAsync(Consumer<DriverReloadResult> onComplete) {
        Task<DriverReloadResult> reloadTask = new Task<>() {
            @Override
            protected DriverReloadResult call() {
                logger.info("Starting driver reload process...");
                updateMessage("Reloading drivers...");

                int originalCount = loadedDrivers.size();
                List<String> reloadedDriverNames = new ArrayList<>();
                List<String> failedDriverNames = new ArrayList<>();

                // Store current drivers to reload
                List<Driver> driversToReload = new ArrayList<>(loadedDrivers);

                if (driversToReload.isEmpty()) {
                    logger.info("No drivers currently loaded to reload");
                    return new DriverReloadResult(0, 0, "No drivers were loaded", null, null);
                }

                int successCount = 0;
                int failureCount = 0;

                // Unregister and re-register each driver
                for (Driver driver : driversToReload) {
                    String driverName = driver.getClass().getName();

                    try {
                        // Unregister
                        DriverManager.deregisterDriver(driver);

                        // Re-register
                        DriverManager.registerDriver(driver);

                        successCount++;
                        reloadedDriverNames.add(driverName);
                        logger.info("Successfully reloaded driver: " + driverName);

                    } catch (SQLException e) {
                        failureCount++;
                        failedDriverNames.add(driverName + " (" + e.getMessage() + ")");
                        logger.log(Level.WARNING, "Failed to reload driver: " + driverName, e);
                    }
                }

                logger.info(String.format("Driver reload complete: %d successful, %d failed out of %d total",
                        successCount, failureCount, originalCount));

                return new DriverReloadResult(successCount, failureCount,
                        "Reloaded " + successCount + " driver(s)", reloadedDriverNames, failedDriverNames);
            }
        };

        reloadTask.setOnSucceeded(event -> onComplete.accept(reloadTask.getValue()));
        reloadTask.setOnFailed(event -> {
            Throwable e = reloadTask.getException();
            logger.log(Level.SEVERE, "Driver reload task failed", e);
            onComplete.accept(new DriverReloadResult(0, 0,
                    "Reload failed: " + (e != null ? e.getMessage() : "Unknown error"),
                    new ArrayList<>(), new ArrayList<>()));
        });

        executorService.submit(reloadTask);
    }

    /**
     * Performs a full refresh: unloads all drivers and reloads them from the JAR files.
     * This is more thorough than reloadAllDriversAsync() as it re-reads the JAR files.
     *
     * @param driversDirectory Path to the directory containing JDBC driver JAR files
     * @param onComplete       Callback invoked when refresh completes
     */
    public void refreshAllDriversAsync(String driversDirectory, Consumer<DriverLoadResult> onComplete) {
        Task<DriverLoadResult> refreshTask = new Task<>() {
            @Override
            protected DriverLoadResult call() {
                logger.info("Starting full driver refresh...");
                updateMessage("Unloading existing drivers...");

                // Store JAR file paths before clearing
                Map<String, String> jarsToReload = new HashMap<>(loadedJarFiles);

                // Unregister all drivers
                unregisterAllDrivers();

                updateMessage("Reloading drivers from disk...");

                if (jarsToReload.isEmpty()) {
                    logger.info("No drivers were previously loaded");
                    // Load all drivers from directory as if it's the first time
                    return loadAllDriversSynchronously(driversDirectory);
                }

                // Reload each JAR file
                int successCount = 0;
                int failureCount = 0;
                List<String> loadedDriverNames = new ArrayList<>();
                List<String> failedJars = new ArrayList<>();

                int currentIndex = 0;
                int totalJars = jarsToReload.size();

                for (Map.Entry<String, String> entry : jarsToReload.entrySet()) {
                    currentIndex++;
                    String jarFileName = entry.getKey();
                    String fullPath = entry.getValue();

                    updateMessage("Reloading " + jarFileName + " (" + currentIndex + "/" + totalJars + ")");
                    updateProgress(currentIndex, totalJars);

                    try {
                        LoadResult result = loadAndRegisterJDBCDriverDynamically(fullPath);

                        if (result.success) {
                            successCount++;
                            loadedDriverNames.addAll(result.driverNames);
                        } else {
                            failureCount++;
                            failedJars.add(jarFileName);
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to reload driver from: " + jarFileName, e);
                        failureCount++;
                        failedJars.add(jarFileName + " (" + e.getMessage() + ")");
                    }
                }

                logger.info(String.format("Driver refresh complete: %d successful, %d failed",
                        successCount, failureCount));

                return new DriverLoadResult(successCount, failureCount,
                        "Refreshed " + successCount + " driver(s)", loadedDriverNames, failedJars);
            }
        };

        refreshTask.setOnSucceeded(event -> onComplete.accept(refreshTask.getValue()));
        refreshTask.setOnFailed(event -> {
            Throwable e = refreshTask.getException();
            logger.log(Level.SEVERE, "Driver refresh failed", e);
            onComplete.accept(new DriverLoadResult(0, 0,
                    "Refresh failed: " + (e != null ? e.getMessage() : "Unknown error")));
        });

        executorService.submit(refreshTask);
    }

    /**
     * Helper method for synchronous loading
     */
    private DriverLoadResult loadAllDriversSynchronously(String driversDirectory) {
        Path driverPath = Paths.get(driversDirectory);
        if (!Files.exists(driverPath) || !Files.isDirectory(driverPath)) {
            return new DriverLoadResult(0, 0, "Directory does not exist");
        }

        Set<String> jarFiles;
        try {
            jarFiles = listJarFiles(driversDirectory);
        } catch (IOException e) {
            return new DriverLoadResult(0, 0, "Failed to scan directory: " + e.getMessage());
        }

        if (jarFiles.isEmpty()) {
            return new DriverLoadResult(0, 0, "No JAR files found");
        }

        int successCount = 0;
        int failureCount = 0;
        List<String> loadedDriverNames = new ArrayList<>();
        List<String> failedJars = new ArrayList<>();

        for (String jarFileName : jarFiles) {
            String fullPath = Paths.get(driversDirectory, jarFileName).toString();
            try {
                LoadResult result = loadAndRegisterJDBCDriverDynamically(fullPath);
                if (result.success) {
                    successCount++;
                    loadedDriverNames.addAll(result.driverNames);
                } else {
                    failureCount++;
                    failedJars.add(jarFileName);
                }
            } catch (Exception e) {
                failureCount++;
                failedJars.add(jarFileName + " (" + e.getMessage() + ")");
            }
        }

        return new DriverLoadResult(successCount, failureCount,
                "Loaded " + successCount + " driver(s)", loadedDriverNames, failedJars);
    }

    // ========== Helper Classes ==========

    /**
     * Result of driver loading operation
     */
    public record DriverLoadResult(int successCount, int failureCount, String message, List<String> loadedDrivers,
                                   List<String> failedJars) {
        public DriverLoadResult(int successCount, int failureCount, String message) {
            this(successCount, failureCount, message, new ArrayList<>(), new ArrayList<>());
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
    public record DriverLoadProgress(int current, int total, String currentFile) {

        public double getPercentage() {
            return total > 0 ? (double) current / total * 100.0 : 0.0;
        }
    }

    /**
     * Driver wrapper to handle ClassLoader issues
     */
    private record DriverShim(Driver driver, URLClassLoader classLoader) implements Driver {

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return driver.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return driver.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
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
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return driver.getParentLogger();
        }
    }

    /**
     * Driver information class
     */
    public record DriverInfo(String className, int majorVersion, int minorVersion, boolean jdbcCompliant) {

        @Override
        public String toString() {
            return String.format("Driver: %s (v%d.%d, JDBC Compliant: %s)",
                    className, majorVersion, minorVersion, jdbcCompliant);
        }
    }

    /**
     * Result of checking for new drivers
     */
    public record NewDriversCheckResult(int newDriverCount, List<String> newDriverJars, String message) {

        public boolean hasNewDrivers() {
            return newDriverCount > 0;
        }

        @Override
        public String toString() {
            return String.format("NewDriversCheckResult{count=%d, message='%s'}",
                    newDriverCount, message);
        }
    }

    /**
     * Result of driver reload operation
     */
    public record DriverReloadResult(int successCount, int failureCount, String message, List<String> reloadedDrivers,
                                     List<String> failedDrivers) {

        public boolean isSuccess() {
            return successCount > 0;
        }

        @Override
        public String toString() {
            return String.format("DriverReloadResult{success=%d, failed=%d, message='%s'}",
                    successCount, failureCount, message);
        }
    }
}