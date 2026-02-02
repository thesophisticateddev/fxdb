package org.fxsql.plugins.runtime;

import org.fxsql.plugins.FXPlugin;
import org.fxsql.plugins.IPlugin;
import org.fxsql.plugins.pluginHook.FXPluginStart;
import org.fxsql.plugins.plugindepdency.FXPluginDependency;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The microkernel is responsible for plugin lifecycle management:
 * - Discovery and loading of plugins
 * - Dependency resolution and injection
 * - Plugin initialization and startup
 * - Plugin shutdown and cleanup
 *
 * Plugins are loaded in isolated threads for safety.
 */
public class FXPluginMicrokernel {

    private static final Logger logger = Logger.getLogger(FXPluginMicrokernel.class.getName());

    private final FXPluginRegistry registry = FXPluginRegistry.INSTANCE;
    private final Map<String, Class<?>> pluginClasses = new ConcurrentHashMap<>();
    private final Map<String, List<String>> dependencyGraph = new ConcurrentHashMap<>();
    private final ExecutorService pluginExecutor;

    private boolean initialized = false;

    public FXPluginMicrokernel() {
        this.pluginExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("PluginKernel-" + t.getId());
            return t;
        });
    }

    /**
     * Initializes the microkernel with the given plugin and dependency class names.
     */
    public void initialize(String[] pluginClasses, String[] dependencyClasses) {
        logger.info("Initializing FXPluginMicrokernel...");

        // Load dependencies first
        List<FXPluginStartupHelper.Pair> dependencies = FXPluginStartupHelper.loadDependencies(dependencyClasses);
        for (FXPluginStartupHelper.Pair dep : dependencies) {
            registry.addInstance(dep.getName(), dep.getInstance());
            logger.fine("Loaded dependency: " + dep.getName());
        }

        // Load plugin classes
        List<Class<FXPlugin>> plugins = FXPluginStartupHelper.loadPlugins(pluginClasses);
        for (Class<?> pluginClass : plugins) {
            FXPlugin annotation = pluginClass.getAnnotation(FXPlugin.class);
            if (annotation != null) {
                this.pluginClasses.put(annotation.id(), pluginClass);
                logger.fine("Registered plugin class: " + annotation.id());
            }
        }

        // Build dependency graph
        buildDependencyGraph();

        initialized = true;
        logger.info("FXPluginMicrokernel initialized with " + this.pluginClasses.size() + " plugins");
    }

    /**
     * Builds a dependency graph for proper initialization order.
     */
    private void buildDependencyGraph() {
        for (Map.Entry<String, Class<?>> entry : pluginClasses.entrySet()) {
            String pluginId = entry.getKey();
            Class<?> pluginClass = entry.getValue();

            List<String> deps = new ArrayList<>();
            Constructor<?>[] constructors = pluginClass.getConstructors();
            if (constructors.length > 0) {
                for (Parameter param : constructors[0].getParameters()) {
                    if (param.isAnnotationPresent(FXPluginDependency.class)) {
                        // Dependency could be another plugin or a service
                        deps.add(param.getType().getSimpleName());
                    }
                }
            }
            dependencyGraph.put(pluginId, deps);
        }
    }

    /**
     * Returns plugins in topological order based on dependencies.
     */
    private List<String> getInitializationOrder() {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String pluginId : pluginClasses.keySet()) {
            if (!visited.contains(pluginId)) {
                topologicalSort(pluginId, visited, inStack, order);
            }
        }

        return order;
    }

    private void topologicalSort(String pluginId, Set<String> visited, Set<String> inStack, List<String> order) {
        if (inStack.contains(pluginId)) {
            logger.warning("Circular dependency detected for plugin: " + pluginId);
            return;
        }
        if (visited.contains(pluginId)) {
            return;
        }

        inStack.add(pluginId);
        List<String> deps = dependencyGraph.getOrDefault(pluginId, Collections.emptyList());
        for (String dep : deps) {
            if (pluginClasses.containsKey(dep)) {
                topologicalSort(dep, visited, inStack, order);
            }
        }
        inStack.remove(pluginId);
        visited.add(pluginId);
        order.add(pluginId);
    }

    /**
     * Instantiates and starts all registered plugins.
     */
    public void startAllPlugins() {
        if (!initialized) {
            logger.warning("Microkernel not initialized");
            return;
        }

        List<String> initOrder = getInitializationOrder();
        logger.info("Starting plugins in order: " + initOrder);

        for (String pluginId : initOrder) {
            startPlugin(pluginId);
        }
    }

    /**
     * Starts a specific plugin by ID.
     */
    public void startPlugin(String pluginId) {
        Class<?> pluginClass = pluginClasses.get(pluginId);
        if (pluginClass == null) {
            logger.warning("Plugin not found: " + pluginId);
            return;
        }

        pluginExecutor.submit(() -> {
            try {
                // Instantiate plugin with dependency injection
                Object pluginInstance = instantiatePlugin(pluginClass);
                if (pluginInstance == null) {
                    logger.severe("Failed to instantiate plugin: " + pluginId);
                    return;
                }

                registry.addInstance(pluginId, pluginInstance);

                // Call @FXPluginStart methods
                invokeStartMethods(pluginInstance);

                // If it implements IPlugin, call lifecycle methods
                if (pluginInstance instanceof IPlugin) {
                    IPlugin plugin = (IPlugin) pluginInstance;
                    plugin.initialize();
                    plugin.start();
                }

                logger.info("Started plugin: " + pluginId);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error starting plugin: " + pluginId, e);
            }
        });
    }

    /**
     * Instantiates a plugin with dependency injection.
     */
    private Object instantiatePlugin(Class<?> pluginClass) throws Exception {
        Constructor<?>[] constructors = pluginClass.getConstructors();
        if (constructors.length == 0) {
            return pluginClass.getDeclaredConstructor().newInstance();
        }

        Constructor<?> constructor = constructors[0];
        Parameter[] params = constructor.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            if (param.isAnnotationPresent(FXPluginDependency.class)) {
                // Look up dependency in registry
                String depName = param.getType().getSimpleName();
                Object dep = registry.get(depName);
                if (dep == null) {
                    // Try by type name
                    for (Object instance : registry.getInstances().values()) {
                        if (param.getType().isInstance(instance)) {
                            dep = instance;
                            break;
                        }
                    }
                }
                args[i] = dep;
            } else {
                args[i] = null;
            }
        }

        return constructor.newInstance(args);
    }

    /**
     * Invokes methods annotated with @FXPluginStart.
     */
    private void invokeStartMethods(Object pluginInstance) {
        for (Method method : pluginInstance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(FXPluginStart.class)) {
                try {
                    method.setAccessible(true);
                    method.invoke(pluginInstance);
                    logger.fine("Invoked @FXPluginStart method: " + method.getName());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error invoking start method: " + method.getName(), e);
                }
            }
        }
    }

    /**
     * Stops a specific plugin by ID.
     */
    public void stopPlugin(String pluginId) {
        Object pluginInstance = registry.get(pluginId);
        if (pluginInstance instanceof IPlugin) {
            ((IPlugin) pluginInstance).stop();
        }
        registry.remove(pluginId);
        logger.info("Stopped plugin: " + pluginId);
    }

    /**
     * Stops all plugins.
     */
    public void stopAllPlugins() {
        // Stop in reverse order
        List<String> initOrder = getInitializationOrder();
        Collections.reverse(initOrder);

        for (String pluginId : initOrder) {
            stopPlugin(pluginId);
        }
    }

    /**
     * Shuts down the microkernel.
     */
    public void shutdown() {
        logger.info("Shutting down FXPluginMicrokernel...");
        stopAllPlugins();

        pluginExecutor.shutdown();
        try {
            if (!pluginExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                pluginExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pluginExecutor.shutdownNow();
        }

        registry.clear();
        pluginClasses.clear();
        dependencyGraph.clear();
        initialized = false;

        logger.info("FXPluginMicrokernel shut down");
    }

    /**
     * Returns whether the microkernel is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the plugin registry.
     */
    public FXPluginRegistry getRegistry() {
        return registry;
    }
}
