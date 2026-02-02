package org.fxsql.plugins;

import org.fxsql.events.EventBus;
import org.fxsql.plugins.events.PluginEvent;
import org.fxsql.plugins.model.PluginInfo;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for FXDB plugins.
 * Provides common functionality for plugin lifecycle management.
 */
public abstract class AbstractPlugin implements IPlugin {

    protected final Logger logger;
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected PluginInfo pluginInfo;
    protected Thread pluginThread;

    public AbstractPlugin() {
        this.logger = Logger.getLogger(getClass().getName());
    }

    @Override
    public void initialize() {
        logger.info("Initializing plugin: " + getName());
        pluginInfo = new PluginInfo();
        pluginInfo.setId(getId());
        pluginInfo.setName(getName());
        pluginInfo.setVersion(getVersion());
        pluginInfo.setStatus(PluginInfo.PluginStatus.INSTALLED);
        onInitialize();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting plugin: " + getName());
            pluginInfo.setStatus(PluginInfo.PluginStatus.LOADING);

            // Run plugin in separate thread
            pluginThread = new Thread(() -> {
                try {
                    onStart();
                    pluginInfo.setStatus(PluginInfo.PluginStatus.RUNNING);
                    EventBus.fireEvent(new PluginEvent(
                            PluginEvent.PLUGIN_STARTED,
                            "Plugin started: " + getName(),
                            getId()
                    ));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error starting plugin: " + getName(), e);
                    pluginInfo.setStatus(PluginInfo.PluginStatus.ERROR);
                    running.set(false);
                    EventBus.fireEvent(new PluginEvent(
                            PluginEvent.PLUGIN_ERROR,
                            "Plugin error: " + getName() + " - " + e.getMessage(),
                            getId()
                    ));
                }
            }, "Plugin-" + getId());

            pluginThread.setDaemon(true);
            pluginThread.start();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping plugin: " + getName());
            try {
                onStop();
                if (pluginThread != null && pluginThread.isAlive()) {
                    pluginThread.interrupt();
                    pluginThread.join(5000); // Wait up to 5 seconds
                }
                pluginInfo.setStatus(PluginInfo.PluginStatus.INSTALLED);
                EventBus.fireEvent(new PluginEvent(
                        PluginEvent.PLUGIN_STOPPED,
                        "Plugin stopped: " + getName(),
                        getId()
                ));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error stopping plugin: " + getName(), e);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public PluginInfo getPluginInfo() {
        return pluginInfo;
    }

    /**
     * Override this to perform plugin-specific initialization.
     */
    protected abstract void onInitialize();

    /**
     * Override this to perform plugin-specific startup logic.
     * This runs in a separate thread.
     */
    protected abstract void onStart();

    /**
     * Override this to perform plugin-specific cleanup.
     */
    protected abstract void onStop();
}