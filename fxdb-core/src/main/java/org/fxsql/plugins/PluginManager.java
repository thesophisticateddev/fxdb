package org.fxsql.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import org.fxsql.events.EventBus;
import org.fxsql.plugins.events.PluginEvent;
import org.fxsql.plugins.model.PluginInfo;
import org.fxsql.plugins.model.PluginManifest;
import org.fxsql.plugins.runtime.FXPluginRegistry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages plugin lifecycle: discovery, loading, starting, and stopping.
 * Plugins run in isolated threads with their own ClassLoaders.
 */
@Singleton
public class PluginManager {

    private static final Logger logger = Logger.getLogger(PluginManager.class.getName());
    private static final String PLUGINS_DIRECTORY = "plugins";
    private static final String MANIFEST_FILE = "plugin-manifest.json";
    private static final String INSTALLED_PLUGINS_FILE = "installed-plugins.json";

    private final Map<String, IPlugin> loadedPlugins = new ConcurrentHashMap<>();
    private final Map<String, URLClassLoader> pluginClassLoaders = new ConcurrentHashMap<>();
    private final ExecutorService pluginExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("PluginWorker-" + t.getId());
        return t;
    });
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PluginManifest manifest;

    public PluginManager() {
        ensurePluginDirectoryExists();
        loadManifest();
        loadInstalledPluginsState();
    }

    private void ensurePluginDirectoryExists() {
        File pluginDir = new File(PLUGINS_DIRECTORY);
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
            logger.info("Created plugins directory: " + pluginDir.getAbsolutePath());
        }
    }

    /**
     * Loads the plugin manifest from resources or creates a default one.
     */
    public void loadManifest() {
        // Try to load from plugins directory first
        File manifestFile = new File(PLUGINS_DIRECTORY, MANIFEST_FILE);
        if (manifestFile.exists()) {
            try {
                manifest = objectMapper.readValue(manifestFile, PluginManifest.class);
                logger.info("Loaded plugin manifest with " + manifest.getPlugins().size() + " plugins");
                return;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load manifest from file", e);
            }
        }

        // Try to load from classpath resources
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(MANIFEST_FILE)) {
            if (is != null) {
                manifest = objectMapper.readValue(is, PluginManifest.class);
                // Copy to plugins directory
                saveManifest();
                logger.info("Loaded plugin manifest from resources");
                return;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load manifest from resources", e);
        }

        // Create default manifest
        manifest = createDefaultManifest();
        saveManifest();
        logger.info("Created default plugin manifest");
    }

    private PluginManifest createDefaultManifest() {
        PluginManifest defaultManifest = new PluginManifest();
        defaultManifest.setManifestVersion("1.0.0");
        defaultManifest.setLastUpdated(java.time.LocalDateTime.now().toString());
        return defaultManifest;
    }

    /**
     * Saves the current manifest to disk.
     */
    public void saveManifest() {
        try {
            File manifestFile = new File(PLUGINS_DIRECTORY, MANIFEST_FILE);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, manifest);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save manifest", e);
        }
    }

    /**
     * Loads installed plugins state from disk.
     */
    private void loadInstalledPluginsState() {
        File stateFile = new File(PLUGINS_DIRECTORY, INSTALLED_PLUGINS_FILE);
        if (stateFile.exists()) {
            try {
                PluginManifest installedState = objectMapper.readValue(stateFile, PluginManifest.class);
                // Update manifest with installed state
                for (PluginInfo installed : installedState.getPlugins()) {
                    PluginInfo manifestPlugin = manifest.getPluginById(installed.getId());
                    if (manifestPlugin != null) {
                        manifestPlugin.setInstalled(installed.isInstalled());
                        manifestPlugin.setEnabled(installed.isEnabled());
                        // Set status based on installed state (runtime status like RUNNING
                        // cannot persist across restarts)
                        if (installed.isInstalled()) {
                            manifestPlugin.setStatus(PluginInfo.PluginStatus.INSTALLED);
                        }
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load installed plugins state", e);
            }
        }
    }

    /**
     * Saves installed plugins state to disk.
     */
    private void saveInstalledPluginsState() {
        try {
            File stateFile = new File(PLUGINS_DIRECTORY, INSTALLED_PLUGINS_FILE);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, manifest);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save installed plugins state", e);
        }
    }

    /**
     * Returns the plugin manifest.
     */
    public PluginManifest getManifest() {
        return manifest;
    }

    /**
     * Installs a plugin from its JAR file.
     */
    public boolean installPlugin(PluginInfo pluginInfo) {
        if (pluginInfo == null || pluginInfo.getJarFile() == null) {
            logger.warning("Cannot install plugin: missing JAR file info");
            return false;
        }

        File jarFile = new File(PLUGINS_DIRECTORY, pluginInfo.getJarFile());
        if (!jarFile.exists()) {
            logger.warning("Plugin JAR not found: " + jarFile.getAbsolutePath());
            return false;
        }

        pluginInfo.setInstalled(true);
        pluginInfo.setStatus(PluginInfo.PluginStatus.INSTALLED);
        saveInstalledPluginsState();

        EventBus.fireEvent(new PluginEvent(PluginEvent.PLUGIN_INSTALLED, "Plugin installed: " + pluginInfo.getName(), pluginInfo.getId()));

        logger.info("Plugin installed: " + pluginInfo.getName());
        return true;
    }

    /**
     * Uninstalls a plugin.
     */
    public boolean uninstallPlugin(String pluginId) {
        // Stop if running
        stopPlugin(pluginId);

        // Unload ClassLoader
        URLClassLoader classLoader = pluginClassLoaders.remove(pluginId);
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing ClassLoader", e);
            }
        }

        loadedPlugins.remove(pluginId);

        PluginInfo info = manifest.getPluginById(pluginId);
        if (info != null) {
            info.setInstalled(false);
            info.setStatus(PluginInfo.PluginStatus.AVAILABLE);
            saveInstalledPluginsState();
        }

        EventBus.fireEvent(new PluginEvent(PluginEvent.PLUGIN_UNINSTALLED, "Plugin uninstalled: " + pluginId, pluginId));

        logger.info("Plugin uninstalled: " + pluginId);
        return true;
    }

    /**
     * Loads a plugin into memory with its own ClassLoader.
     */
    public IPlugin loadPlugin(PluginInfo pluginInfo) {
        if (pluginInfo == null || !pluginInfo.isInstalled()) {
            logger.warning("Cannot load plugin: not installed");
            return null;
        }

        if (loadedPlugins.containsKey(pluginInfo.getId())) {
            return loadedPlugins.get(pluginInfo.getId());
        }

        try {
            File jarFile = new File(PLUGINS_DIRECTORY, pluginInfo.getJarFile());
            if (!jarFile.exists()) {
                logger.warning("Plugin JAR not found: " + jarFile.getAbsolutePath());
                return null;
            }

            // Create isolated ClassLoader for the plugin
            URLClassLoader classLoader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, getClass().getClassLoader());
            pluginClassLoaders.put(pluginInfo.getId(), classLoader);

            // Load the plugin class
            Class<?> pluginClass = classLoader.loadClass(pluginInfo.getMainClass());
            if (!IPlugin.class.isAssignableFrom(pluginClass)) {
                logger.warning("Plugin class does not implement IPlugin: " + pluginInfo.getMainClass());
                return null;
            }

            IPlugin plugin = (IPlugin) pluginClass.getDeclaredConstructor().newInstance();
            plugin.initialize();

            loadedPlugins.put(pluginInfo.getId(), plugin);
            FXPluginRegistry.INSTANCE.addInstance(pluginInfo.getId(), plugin);

            pluginInfo.setStatus(PluginInfo.PluginStatus.INSTALLED);

            EventBus.fireEvent(new PluginEvent(PluginEvent.PLUGIN_LOADED, "Plugin loaded: " + pluginInfo.getName(), pluginInfo.getId()));

            logger.info("Plugin loaded: " + pluginInfo.getName());
            return plugin;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load plugin: " + pluginInfo.getName(), e);
            pluginInfo.setStatus(PluginInfo.PluginStatus.ERROR);
            return null;
        }
    }

    /**
     * Starts a plugin in its own thread.
     */
    public boolean startPlugin(String pluginId) {
        IPlugin plugin = loadedPlugins.get(pluginId);
        if (plugin == null) {
            PluginInfo info = manifest.getPluginById(pluginId);
            if (info != null && info.isInstalled()) {
                plugin = loadPlugin(info);
            }
        }

        if (plugin == null) {
            logger.warning("Cannot start plugin: not loaded - " + pluginId);
            return false;
        }

        if (plugin.isRunning()) {
            logger.info("Plugin already running: " + pluginId);
            return true;
        }

        final IPlugin finalPlugin = plugin;
        pluginExecutor.submit(() -> {
            try {
                finalPlugin.start();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error starting plugin: " + pluginId, e);
            }
        });

        return true;
    }

    /**
     * Stops a running plugin.
     */
    public boolean stopPlugin(String pluginId) {
        IPlugin plugin = loadedPlugins.get(pluginId);
        if (plugin == null) {
            return false;
        }

        if (!plugin.isRunning()) {
            return true;
        }

        plugin.stop();
        return true;
    }

    /**
     * Returns a loaded plugin by ID.
     */
    public IPlugin getPlugin(String pluginId) {
        return loadedPlugins.get(pluginId);
    }

    /**
     * Returns all loaded plugins.
     */
    public Map<String, IPlugin> getLoadedPlugins() {
        return new ConcurrentHashMap<>(loadedPlugins);
    }

    /**
     * Starts all enabled and installed plugins.
     */
    public void startAllEnabledPlugins() {
        for (PluginInfo info : manifest.getPlugins()) {
            if (info.isInstalled() && info.isEnabled()) {
                startPlugin(info.getId());
            }
        }
    }

    /**
     * Stops all running plugins.
     */
    public void stopAllPlugins() {
        for (String pluginId : loadedPlugins.keySet()) {
            stopPlugin(pluginId);
        }
    }

    /**
     * Shuts down the plugin manager.
     */
    public void shutdown() {
        logger.info("Shutting down plugin manager...");
        stopAllPlugins();

        // Close all ClassLoaders
        for (URLClassLoader classLoader : pluginClassLoaders.values()) {
            try {
                classLoader.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing ClassLoader", e);
            }
        }
        pluginClassLoaders.clear();
        loadedPlugins.clear();

        // Shutdown executor
        pluginExecutor.shutdown();
        try {
            if (!pluginExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                pluginExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pluginExecutor.shutdownNow();
        }

        saveInstalledPluginsState();
        logger.info("Plugin manager shut down");
    }
}