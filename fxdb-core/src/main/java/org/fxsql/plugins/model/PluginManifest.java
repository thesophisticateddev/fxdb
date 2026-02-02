package org.fxsql.plugins.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the plugin manifest containing all available plugins.
 */
public class PluginManifest {
    private String manifestVersion;
    private String lastUpdated;
    private List<PluginInfo> plugins;

    public PluginManifest() {
        this.plugins = new ArrayList<>();
    }

    public String getManifestVersion() {
        return manifestVersion;
    }

    public void setManifestVersion(String manifestVersion) {
        this.manifestVersion = manifestVersion;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<PluginInfo> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<PluginInfo> plugins) {
        this.plugins = plugins;
    }

    public void addPlugin(PluginInfo plugin) {
        if (this.plugins == null) {
            this.plugins = new ArrayList<>();
        }
        this.plugins.add(plugin);
    }

    public PluginInfo getPluginById(String id) {
        return plugins.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}