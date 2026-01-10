package org.fxsql.plugins.runtime;


import java.util.HashMap;
import java.util.Map;

/**
 * Holds dependency {@link io.github.ivangavlik.PonderaAssembly.plugin.dependency.DependencyInstance}
 * and plugin {@link io.github.ivangavlik.PonderaAssembly.plugin.Plugin} instances.
 */
public class FXPluginRegistry {
    static FXPluginRegistry INSTANCE = new FXPluginRegistry();

    // TODO order is not handled - ordershoud be as used is declared plugin also take account depencies and extension points
    private Map<String, Object> instances = new HashMap<>();
    private FXPluginRegistry() {}
    public void addInstance(String key, Object value) {
        instances.put(key, value);
    }
    public Object get(String key) {
        return instances.get(key);
    }
    public boolean exist(String key) {
        return instances.get(key) == null;
    }
    public Map<String, Object> getInstances() {
        return this.instances;
    }
}
