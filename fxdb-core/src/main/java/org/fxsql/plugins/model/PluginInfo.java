package org.fxsql.plugins.model;

import java.util.List;

/**
 * Represents metadata about a plugin from the manifest.
 */
public class PluginInfo {
    private String id;
    private String name;
    private String version;
    private String description;
    private String author;
    private String category;
    private String mainClass;
    private String jarFile;
    private boolean enabled;
    private boolean installed;
    private List<String> dependencies;
    private PluginStatus status;

    public enum PluginStatus {
        AVAILABLE,      // In manifest but not installed
        INSTALLED,      // Installed but not running
        RUNNING,        // Currently active
        DISABLED,       // Installed but disabled
        ERROR,          // Failed to load/run
        LOADING         // Currently being loaded
    }

    public PluginInfo() {
        this.status = PluginStatus.AVAILABLE;
        this.enabled = true;
        this.installed = false;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public String getJarFile() {
        return jarFile;
    }

    public void setJarFile(String jarFile) {
        this.jarFile = jarFile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public PluginStatus getStatus() {
        return status;
    }

    public void setStatus(PluginStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return String.format("Plugin[%s v%s - %s]", name, version, status);
    }
}