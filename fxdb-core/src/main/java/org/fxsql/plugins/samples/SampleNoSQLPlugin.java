package org.fxsql.plugins.samples;

import org.fxsql.events.EventBus;
import org.fxsql.plugins.AbstractPlugin;
import org.fxsql.plugins.FXPlugin;
import org.fxsql.plugins.events.PluginEvent;
import org.fxsql.plugins.pluginHook.FXPluginStart;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Sample NoSQL Database Plugin.
 * This is a template showing how to create a database connector plugin.
 *
 * NoSQL plugins should:
 * 1. Run connection operations in a separate thread
 * 2. Use a command queue for thread-safe operations
 * 3. Fire events for connection state changes
 * 4. Implement proper cleanup on shutdown
 */
@FXPlugin(id = "nosql-sample")
public class SampleNoSQLPlugin extends AbstractPlugin {

    private static final String VERSION = "1.0.0";
    private static final String NAME = "Sample NoSQL Connector";

    // Connection state
    private volatile boolean connected = false;
    private String connectionUri;

    // Command queue for thread-safe operations
    private final BlockingQueue<DatabaseCommand> commandQueue = new LinkedBlockingQueue<>();

    // Simulated connection worker thread
    private Thread workerThread;
    private volatile boolean workerRunning = false;

    /**
     * Represents a database command to be executed.
     */
    public static class DatabaseCommand {
        public enum Type { CONNECT, DISCONNECT, QUERY, INSERT, UPDATE, DELETE }

        private final Type type;
        private final String payload;
        private final CommandCallback callback;

        public DatabaseCommand(Type type, String payload, CommandCallback callback) {
            this.type = type;
            this.payload = payload;
            this.callback = callback;
        }

        public Type getType() { return type; }
        public String getPayload() { return payload; }
        public CommandCallback getCallback() { return callback; }
    }

    @FunctionalInterface
    public interface CommandCallback {
        void onComplete(boolean success, Object result, String error);
    }

    @Override
    public String getId() {
        return "nosql-sample";
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    protected void onInitialize() {
        logger.info("Initializing " + NAME);
    }

    @FXPluginStart
    public void onPluginStart() {
        logger.info("NoSQL plugin started via @FXPluginStart");
    }

    @Override
    protected void onStart() {
        logger.info("Starting " + NAME);

        // Start worker thread for database operations
        workerRunning = true;
        workerThread = new Thread(this::processCommands, "NoSQL-Worker-" + getId());
        workerThread.setDaemon(true);
        workerThread.start();

        EventBus.fireEvent(new PluginEvent(
                PluginEvent.PLUGIN_STARTED,
                "NoSQL connector service is ready",
                getId()
        ));
    }

    @Override
    protected void onStop() {
        logger.info("Stopping " + NAME);

        // Signal worker to stop
        workerRunning = false;

        // Add a poison pill to unblock the worker
        commandQueue.offer(new DatabaseCommand(DatabaseCommand.Type.DISCONNECT, null, null));

        // Wait for worker to finish
        if (workerThread != null) {
            try {
                workerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Disconnect if still connected
        if (connected) {
            disconnect();
        }

        commandQueue.clear();
    }

    /**
     * Worker thread that processes database commands.
     */
    private void processCommands() {
        logger.info("Worker thread started");

        while (workerRunning) {
            try {
                DatabaseCommand command = commandQueue.poll(1, TimeUnit.SECONDS);
                if (command == null) {
                    continue;
                }

                processCommand(command);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.severe("Error processing command: " + e.getMessage());
            }
        }

        logger.info("Worker thread stopped");
    }

    private void processCommand(DatabaseCommand command) {
        boolean success = false;
        Object result = null;
        String error = null;

        try {
            switch (command.getType()) {
                case CONNECT:
                    success = doConnect(command.getPayload());
                    break;
                case DISCONNECT:
                    success = doDisconnect();
                    break;
                case QUERY:
                    result = doQuery(command.getPayload());
                    success = result != null;
                    break;
                case INSERT:
                case UPDATE:
                case DELETE:
                    success = doWrite(command.getType(), command.getPayload());
                    break;
            }
        } catch (Exception e) {
            error = e.getMessage();
            logger.severe("Command failed: " + error);
        }

        if (command.getCallback() != null) {
            command.getCallback().onComplete(success, result, error);
        }
    }

    /**
     * Simulated connect operation.
     */
    private boolean doConnect(String uri) {
        logger.info("Connecting to: " + uri);
        // Simulate connection delay
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        this.connectionUri = uri;
        this.connected = true;

        EventBus.fireEvent(new PluginEvent(
                PluginEvent.PLUGIN_EVENT,
                "Connected to NoSQL database: " + uri,
                getId()
        ));

        return true;
    }

    /**
     * Simulated disconnect operation.
     */
    private boolean doDisconnect() {
        if (!connected) {
            return true;
        }

        logger.info("Disconnecting from: " + connectionUri);
        this.connected = false;
        this.connectionUri = null;

        EventBus.fireEvent(new PluginEvent(
                PluginEvent.PLUGIN_EVENT,
                "Disconnected from NoSQL database",
                getId()
        ));

        return true;
    }

    /**
     * Simulated query operation.
     */
    private Object doQuery(String query) {
        if (!connected) {
            throw new IllegalStateException("Not connected");
        }

        logger.info("Executing query: " + query);
        // Simulate query execution
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        // Return simulated result
        return "Query result for: " + query;
    }

    /**
     * Simulated write operation.
     */
    private boolean doWrite(DatabaseCommand.Type type, String payload) {
        if (!connected) {
            throw new IllegalStateException("Not connected");
        }

        logger.info("Executing " + type + ": " + payload);
        // Simulate write operation
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return true;
    }

    // Public API methods that queue commands

    /**
     * Connects to a NoSQL database asynchronously.
     */
    public void connect(String uri, CommandCallback callback) {
        commandQueue.offer(new DatabaseCommand(DatabaseCommand.Type.CONNECT, uri, callback));
    }

    /**
     * Disconnects from the database asynchronously.
     */
    public void disconnect(CommandCallback callback) {
        commandQueue.offer(new DatabaseCommand(DatabaseCommand.Type.DISCONNECT, null, callback));
    }

    /**
     * Disconnects synchronously (for shutdown).
     */
    public void disconnect() {
        doDisconnect();
    }

    /**
     * Executes a query asynchronously.
     */
    public void query(String query, CommandCallback callback) {
        commandQueue.offer(new DatabaseCommand(DatabaseCommand.Type.QUERY, query, callback));
    }

    /**
     * Returns whether currently connected.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Returns the current connection URI.
     */
    public String getConnectionUri() {
        return connectionUri;
    }
}