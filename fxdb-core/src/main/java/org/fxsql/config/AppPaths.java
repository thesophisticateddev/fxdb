package org.fxsql.config;

import java.io.File;
import java.nio.file.Path;

/**
 * Resolves application data directories to a user-writable location.
 * On Windows: %APPDATA%/fxdb/
 * On macOS:   ~/Library/Application Support/fxdb/
 * On Linux:   ~/.fxdb/
 *
 * In dev mode (-Dfxdb.dev=true), uses the current working directory instead.
 */
public final class AppPaths {

    private static final String APP_NAME = "fxdb";
    private static final boolean DEV_MODE = Boolean.getBoolean("fxdb.dev");
    private static final Path APP_DATA_DIR = resolveAppDataDir();

    private AppPaths() {}

    public static boolean isDevMode() {
        return DEV_MODE;
    }

    /**
     * Returns the root application data directory.
     */
    public static Path getAppDataDir() {
        return APP_DATA_DIR;
    }

    /**
     * Returns a subdirectory under the app data directory.
     * Creates it if it doesn't exist.
     */
    public static File getDir(String subdir) {
        File dir = APP_DATA_DIR.resolve(subdir).toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Returns a file path under the app data directory.
     */
    public static File getFile(String subdir, String filename) {
        File dir = getDir(subdir);
        return new File(dir, filename);
    }

    /**
     * Returns a file path directly under the app data root.
     */
    public static File getFile(String filename) {
        File dir = APP_DATA_DIR.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, filename);
    }

    private static Path resolveAppDataDir() {
        if (DEV_MODE) {
            return Path.of(System.getProperty("user.dir"));
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        Path base;

        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null && !appdata.isEmpty()) {
                base = Path.of(appdata, APP_NAME);
            } else {
                base = Path.of(System.getProperty("user.home"), "AppData", "Roaming", APP_NAME);
            }
        } else if (os.contains("mac")) {
            base = Path.of(System.getProperty("user.home"), "Library", "Application Support", APP_NAME);
        } else {
            base = Path.of(System.getProperty("user.home"), "." + APP_NAME);
        }

        File dir = base.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return base;
    }
}
