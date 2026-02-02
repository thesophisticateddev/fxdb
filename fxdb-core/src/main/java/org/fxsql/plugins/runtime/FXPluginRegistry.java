package org.fxsql.plugins.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Thread-safe registry for plugin and dependency instances.
 * Manages the lifecycle of plugin instances within the framework.
 */
public class FXPluginRegistry {
    private static final Logger logger = Logger.getLogger(FXPluginRegistry.class.getName());

    public static final FXPluginRegistry INSTANCE = new FXPluginRegistry();

    private final Map<String, Object> instances = new ConcurrentHashMap<>();
    private final Map<String, Integer> loadOrder = new ConcurrentHashMap<>();
    private int orderCounter = 0;

    private FXPluginRegistry() {}

    /**
     * Adds an instance to the registry.
     */
    public void addInstance(String key, Object value) {
        instances.put(key, value);
        loadOrder.put(key, orderCounter++);
        logger.fine("Registered instance: " + key);
    }

    /**
     * Gets an instance by key.
     */
    public Object get(String key) {
        return instances.get(key);
    }

    /**
     * Gets an instance by key with type casting.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object instance = instances.get(key);
        if (instance != null && type.isInstance(instance)) {
            return (T) instance;
        }
        return null;
    }

    /**
     * Checks if an instance exists for the given key.
     */
    public boolean exists(String key) {
        return instances.containsKey(key);
    }

    /**
     * Removes an instance from the registry.
     */
    public Object remove(String key) {
        loadOrder.remove(key);
        return instances.remove(key);
    }

    /**
     * Returns all registered instances.
     */
    public Map<String, Object> getInstances() {
        return Collections.unmodifiableMap(instances);
    }

    /**
     * Returns all registered keys.
     */
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(instances.keySet());
    }

    /**
     * Returns the load order for a given key.
     */
    public int getLoadOrder(String key) {
        return loadOrder.getOrDefault(key, -1);
    }

    /**
     * Clears all instances from the registry.
     */
    public void clear() {
        instances.clear();
        loadOrder.clear();
        orderCounter = 0;
        logger.info("Registry cleared");
    }

    /**
     * Returns the number of registered instances.
     */
    public int size() {
        return instances.size();
    }
}
