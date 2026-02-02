package org.fxsql.plugins;

import org.fxsql.plugins.model.PluginInfo;

/**
 * Base interface that all FXDB plugins must implement.
 * Plugins run in their own thread and communicate via the event bus.
 */
public interface IPlugin {

    /**
     * Called when the plugin is initialized.
     * Use this for setup tasks that don't require UI.
     */
    void initialize();

    /**
     * Called when the plugin is started.
     * The plugin should begin its main operations here.
     */
    void start();

    /**
     * Called when the plugin is stopped.
     * The plugin should clean up resources here.
     */
    void stop();

    /**
     * Returns the plugin's unique identifier.
     */
    String getId();

    /**
     * Returns the plugin's display name.
     */
    String getName();

    /**
     * Returns the plugin's version.
     */
    String getVersion();

    /**
     * Returns whether the plugin is currently running.
     */
    boolean isRunning();

    /**
     * Returns the plugin's information.
     */
    default PluginInfo getPluginInfo() {
        PluginInfo info = new PluginInfo();
        info.setId(getId());
        info.setName(getName());
        info.setVersion(getVersion());
        return info;
    }
}