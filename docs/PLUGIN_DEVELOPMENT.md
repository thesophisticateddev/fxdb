# FXDB Plugin Development Guide

This guide explains how to create plugins for FXDB, the JavaFX database management application.

## Table of Contents

1. [Overview](#overview)
2. [Plugin Architecture](#plugin-architecture)
3. [Getting Started](#getting-started)
4. [Plugin Lifecycle](#plugin-lifecycle)
5. [Creating Your First Plugin](#creating-your-first-plugin)
6. [Plugin Manifest](#plugin-manifest)
7. [Threading Model](#threading-model)
8. [Event System](#event-system)
9. [Dependency Injection](#dependency-injection)
10. [Plugin Categories](#plugin-categories)
11. [Best Practices](#best-practices)
12. [Example Plugins](#example-plugins)
13. [Packaging and Distribution](#packaging-and-distribution)
14. [Troubleshooting](#troubleshooting)

---

## Overview

FXDB supports a plugin system that allows developers to extend the application's functionality. Plugins can:

- Add support for new database types (NoSQL, cloud databases)
- Provide syntax highlighting and code completion
- Add data export/import capabilities
- Create custom UI components and themes
- Implement custom tools and utilities

**Key Features:**
- Plugins run in isolated threads for stability
- Each plugin has its own ClassLoader for isolation
- Communication via an event bus
- Simple annotation-based configuration

---

## Plugin Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      FXDB Application                        │
├─────────────────────────────────────────────────────────────┤
│                      Plugin Manager                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │  Plugin A   │  │  Plugin B   │  │  Plugin C   │         │
│  │ (Thread A)  │  │ (Thread B)  │  │ (Thread C)  │         │
│  │ ClassLoader │  │ ClassLoader │  │ ClassLoader │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                  │
│         └────────────────┼────────────────┘                  │
│                          ▼                                   │
│                    ┌───────────┐                            │
│                    │ Event Bus │                            │
│                    └───────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

### Components

| Component | Description |
|-----------|-------------|
| `PluginManager` | Central service that loads, starts, stops, and manages plugins |
| `IPlugin` | Interface that all plugins must implement |
| `AbstractPlugin` | Base class providing common functionality |
| `FXPluginRegistry` | Registry storing plugin instances |
| `EventBus` | Pub/sub system for inter-plugin communication |

---

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- FXDB source code (for development)

### Project Setup

Create a new Maven project for your plugin:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-fxdb-plugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- FXDB Core (provided at runtime) -->
        <dependency>
            <groupId>org.fxsql</groupId>
            <artifactId>fxdb-core</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <archive>
                        <manifest>
                            <addDefaultImplementationEntries>true</addDefaultImplementationEntries>
                        </manifest>
                        <manifestEntries>
                            <Plugin-Id>my-plugin</Plugin-Id>
                            <Plugin-Version>1.0.0</Plugin-Version>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Plugin Lifecycle

Plugins follow a defined lifecycle:

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  AVAILABLE  │────▶│  INSTALLED  │────▶│   LOADING   │────▶│   RUNNING   │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                           │                                       │
                           │                                       │
                           ▼                                       ▼
                    ┌─────────────┐                         ┌─────────────┐
                    │   DISABLED  │                         │   STOPPED   │
                    └─────────────┘                         └─────────────┘
                                                                   │
                                                                   ▼
                                                            ┌─────────────┐
                                                            │    ERROR    │
                                                            └─────────────┘
```

### Lifecycle Methods

| Method | When Called | Thread |
|--------|-------------|--------|
| `initialize()` | After plugin is loaded | Plugin thread |
| `start()` | When plugin is started | Plugin thread |
| `stop()` | When plugin is stopped | Plugin thread |

---

## Creating Your First Plugin

### Step 1: Implement the Plugin Interface

```java
package com.example.myplugin;

import org.fxsql.plugins.AbstractPlugin;
import org.fxsql.plugins.FXPlugin;

@FXPlugin(id = "my-first-plugin")
public class MyFirstPlugin extends AbstractPlugin {

    @Override
    public String getId() {
        return "my-first-plugin";
    }

    @Override
    public String getName() {
        return "My First Plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    protected void onInitialize() {
        // Called when plugin is loaded
        // Use for one-time setup (no heavy operations)
        logger.info("Plugin initialized!");
    }

    @Override
    protected void onStart() {
        // Called when plugin is started
        // This runs in a separate thread
        // Put your main plugin logic here
        logger.info("Plugin started!");

        // Example: Do some work
        while (isRunning()) {
            // Plugin work loop
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    @Override
    protected void onStop() {
        // Called when plugin is stopped
        // Clean up resources here
        logger.info("Plugin stopped!");
    }
}
```

### Step 2: Using Annotations (Optional)

You can use annotations for additional lifecycle hooks:

```java
import org.fxsql.plugins.pluginHook.FXPluginStart;
import org.fxsql.plugins.pluginEvents.FXPluginHandleEvent;

@FXPlugin(id = "annotated-plugin")
public class AnnotatedPlugin extends AbstractPlugin {

    @FXPluginStart
    public void onPluginStartAnnotation() {
        // Called after initialize(), before onStart()
        logger.info("Plugin starting via annotation!");
    }

    @FXPluginHandleEvent
    public void handleEvent(Object event) {
        // Called when events are fired
        logger.info("Received event: " + event);
    }

    // ... implement abstract methods
}
```

---

## Plugin Manifest

Every plugin must be registered in the `plugin-manifest.json` file.

### Manifest Location

- Development: `fxdb-ui/src/main/resources/plugin-manifest.json`
- Runtime: `plugins/plugin-manifest.json`

### Manifest Structure

```json
{
  "manifestVersion": "1.0.0",
  "lastUpdated": "2026-02-02T00:00:00",
  "plugins": [
    {
      "id": "my-first-plugin",
      "name": "My First Plugin",
      "version": "1.0.0",
      "description": "A detailed description of what your plugin does.",
      "author": "Your Name",
      "category": "Tools",
      "mainClass": "com.example.myplugin.MyFirstPlugin",
      "jarFile": "my-fxdb-plugin-1.0.0.jar",
      "enabled": true,
      "installed": false,
      "dependencies": []
    }
  ]
}
```

### Manifest Fields

| Field | Required | Description |
|-------|----------|-------------|
| `id` | Yes | Unique identifier (lowercase, hyphens allowed) |
| `name` | Yes | Display name |
| `version` | Yes | Semantic version (e.g., "1.0.0") |
| `description` | Yes | Detailed description for users |
| `author` | Yes | Plugin author name |
| `category` | Yes | One of: Database, Editor, Tools, Themes |
| `mainClass` | Yes | Fully qualified class name of plugin entry point |
| `jarFile` | Yes | JAR filename (placed in plugins directory) |
| `enabled` | No | Whether plugin should auto-start (default: true) |
| `installed` | No | Installation state (managed by system) |
| `dependencies` | No | Array of plugin IDs this plugin depends on |

---

## Threading Model

**Important:** Plugins run in their own threads. This provides isolation but requires thread-safe coding.

### Guidelines

1. **Never block the JavaFX Application Thread**
   ```java
   // WRONG - blocks UI
   @Override
   protected void onStart() {
       Platform.runLater(() -> {
           while (true) { /* infinite loop */ }
       });
   }

   // CORRECT - runs in plugin thread
   @Override
   protected void onStart() {
       while (isRunning()) {
           // Your work here
           Thread.sleep(100);
       }
   }
   ```

2. **Use Platform.runLater() for UI updates**
   ```java
   import javafx.application.Platform;

   @Override
   protected void onStart() {
       // Update UI safely
       Platform.runLater(() -> {
           someLabel.setText("Updated from plugin!");
       });
   }
   ```

3. **Check isRunning() in loops**
   ```java
   @Override
   protected void onStart() {
       while (isRunning()) {
           doWork();

           try {
               Thread.sleep(1000);
           } catch (InterruptedException e) {
               // Plugin is being stopped
               break;
           }
       }
   }
   ```

4. **Use concurrent collections for shared data**
   ```java
   import java.util.concurrent.ConcurrentHashMap;
   import java.util.concurrent.BlockingQueue;
   import java.util.concurrent.LinkedBlockingQueue;

   private final Map<String, Object> cache = new ConcurrentHashMap<>();
   private final BlockingQueue<Command> commandQueue = new LinkedBlockingQueue<>();
   ```

---

## Event System

Plugins communicate with the application and each other via the EventBus.

### Firing Events

```java
import org.fxsql.events.EventBus;
import org.fxsql.plugins.events.PluginEvent;

// Fire a plugin event
EventBus.fireEvent(new PluginEvent(
    PluginEvent.PLUGIN_EVENT,
    "Something happened in my plugin",
    getId()
));
```

### Listening for Events

```java
import javafx.event.EventHandler;
import org.fxsql.events.EventBus;
import org.fxsql.plugins.events.PluginEvent;

@Override
protected void onInitialize() {
    // Register event handler
    EventHandler<PluginEvent> handler = event -> {
        logger.info("Received: " + event.getMessage());
    };

    EventBus.addEventHandler(PluginEvent.PLUGIN_EVENT, handler);
}
```

### Available Event Types

| Event Type | Description |
|------------|-------------|
| `PluginEvent.PLUGIN_INSTALLED` | Plugin was installed |
| `PluginEvent.PLUGIN_UNINSTALLED` | Plugin was uninstalled |
| `PluginEvent.PLUGIN_STARTED` | Plugin started running |
| `PluginEvent.PLUGIN_STOPPED` | Plugin stopped |
| `PluginEvent.PLUGIN_ERROR` | Plugin encountered an error |
| `PluginEvent.PLUGIN_LOADED` | Plugin was loaded into memory |

### Creating Custom Events

```java
import javafx.event.Event;
import javafx.event.EventType;
import org.fxsql.events.IEvent;

public class MyCustomEvent extends Event implements IEvent {

    public static final EventType<MyCustomEvent> MY_EVENT =
        new EventType<>(Event.ANY, "MY_CUSTOM_EVENT");

    private final String data;

    public MyCustomEvent(String data) {
        super(MY_EVENT);
        this.data = data;
    }

    @Override
    public String getMessage() {
        return data;
    }

    public String getData() {
        return data;
    }
}
```

---

## Dependency Injection

Plugins can declare dependencies that are injected at construction time.

### Declaring Dependencies

```java
import org.fxsql.plugins.plugindepdency.FXPluginDependency;

@FXPlugin(id = "dependent-plugin")
public class DependentPlugin extends AbstractPlugin {

    private final SomeService service;

    public DependentPlugin(
            @FXPluginDependency SomeService service) {
        this.service = service;
    }

    // ... rest of implementation
}
```

### Creating Dependency Factories

```java
import org.fxsql.plugins.plugindepdency.FXPluginDependencyFactory;
import org.fxsql.plugins.plugindepdency.FXDependencyInstance;

@FXPluginDependencyFactory
public class MyDependencyFactory {

    @FXDependencyInstance
    public SomeService someService() {
        return new SomeServiceImpl();
    }

    @FXDependencyInstance
    public AnotherService anotherService() {
        return new AnotherServiceImpl();
    }
}
```

---

## Plugin Categories

Plugins are organized into categories:

### Database
Plugins that add support for new database types.

```java
@FXPlugin(id = "mongodb-connector")
public class MongoDBPlugin extends AbstractPlugin {
    // Implement database connection logic
}
```

### Editor
Plugins that enhance the SQL editor.

```java
@FXPlugin(id = "syntax-highlighter")
public class SyntaxHighlighterPlugin extends AbstractPlugin {
    // Implement syntax highlighting
}
```

### Tools
Utility plugins for data manipulation, export, etc.

```java
@FXPlugin(id = "data-export")
public class DataExportPlugin extends AbstractPlugin {
    // Implement export functionality
}
```

### Themes
Visual customization plugins.

```java
@FXPlugin(id = "dark-theme")
public class DarkThemePlugin extends AbstractPlugin {
    // Apply custom stylesheets
}
```

---

## Best Practices

### 1. Resource Management

```java
@Override
protected void onStop() {
    // Always clean up resources
    if (connection != null) {
        connection.close();
    }
    if (executor != null) {
        executor.shutdown();
    }
}
```

### 2. Error Handling

```java
@Override
protected void onStart() {
    try {
        riskyOperation();
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Operation failed", e);

        // Notify the system
        EventBus.fireEvent(new PluginEvent(
            PluginEvent.PLUGIN_ERROR,
            "Error: " + e.getMessage(),
            getId()
        ));
    }
}
```

### 3. Configuration

Store plugin configuration in the plugins directory:

```java
private Properties loadConfig() {
    Properties props = new Properties();
    File configFile = new File("plugins", getId() + ".properties");

    if (configFile.exists()) {
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
        } catch (IOException e) {
            logger.warning("Could not load config: " + e.getMessage());
        }
    }

    return props;
}
```

### 4. Logging

Use the provided logger:

```java
// Available in AbstractPlugin
logger.info("Information message");
logger.warning("Warning message");
logger.severe("Error message");
logger.fine("Debug message");
```

### 5. Graceful Shutdown

```java
private volatile boolean shouldStop = false;

@Override
protected void onStart() {
    while (isRunning() && !shouldStop) {
        // Work
    }
}

@Override
protected void onStop() {
    shouldStop = true;
    // Wait for work to complete
}
```

---

## Example Plugins

### Database Connector Plugin

```java
@FXPlugin(id = "redis-connector")
public class RedisConnectorPlugin extends AbstractPlugin {

    private RedisClient client;
    private final BlockingQueue<Command> commands = new LinkedBlockingQueue<>();

    @Override
    public String getId() { return "redis-connector"; }

    @Override
    public String getName() { return "Redis Connector"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    protected void onInitialize() {
        logger.info("Redis connector initialized");
    }

    @Override
    protected void onStart() {
        logger.info("Starting Redis connector");

        while (isRunning()) {
            try {
                Command cmd = commands.poll(1, TimeUnit.SECONDS);
                if (cmd != null) {
                    processCommand(cmd);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    @Override
    protected void onStop() {
        if (client != null) {
            client.shutdown();
        }
        commands.clear();
    }

    public void connect(String host, int port) {
        commands.offer(new Command("CONNECT", host + ":" + port));
    }

    public void executeCommand(String command) {
        commands.offer(new Command("EXECUTE", command));
    }

    private void processCommand(Command cmd) {
        // Process the command
    }
}
```

### UI Enhancement Plugin

```java
@FXPlugin(id = "status-bar-plugin")
public class StatusBarPlugin extends AbstractPlugin {

    @Override
    public String getId() { return "status-bar-plugin"; }

    @Override
    public String getName() { return "Enhanced Status Bar"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    protected void onInitialize() {
        // Nothing to initialize
    }

    @Override
    protected void onStart() {
        // Update status bar periodically
        while (isRunning()) {
            Platform.runLater(this::updateStatusBar);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    @Override
    protected void onStop() {
        // Cleanup
    }

    private void updateStatusBar() {
        // Update UI components
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;

        // Fire event with memory info
        EventBus.fireEvent(new PluginEvent(
            PluginEvent.PLUGIN_EVENT,
            "Memory: " + usedMemory + " MB",
            getId()
        ));
    }
}
```

---

## Packaging and Distribution

### Building Your Plugin

```bash
mvn clean package
```

This creates: `target/my-fxdb-plugin-1.0.0.jar`

### Installation

1. Copy the JAR to the `plugins/` directory
2. Add an entry to `plugins/plugin-manifest.json`
3. Restart FXDB or use the Plugin Manager to install

### Distribution Checklist

- [ ] JAR file with all dependencies (or use provided scope)
- [ ] Manifest entry with accurate information
- [ ] README with installation instructions
- [ ] License file
- [ ] Version follows semantic versioning

---

## Troubleshooting

### Plugin Won't Load

1. Check the JAR is in the `plugins/` directory
2. Verify `mainClass` in manifest matches your class
3. Check logs for ClassNotFoundException
4. Ensure plugin implements `IPlugin` interface

### Plugin Crashes on Start

1. Check for null pointer exceptions in `onInitialize()`
2. Verify all dependencies are available
3. Check thread safety of shared resources

### Events Not Received

1. Ensure handler is registered before events are fired
2. Check event type matches exactly
3. Verify EventBus import is from `org.fxsql.events`

### UI Updates Not Showing

1. Use `Platform.runLater()` for all UI updates
2. Check component references are not null
3. Verify you're updating the correct component

### Memory Leaks

1. Always unregister event handlers in `onStop()`
2. Close streams and connections
3. Clear collections holding references

---

## API Reference

### IPlugin Interface

```java
public interface IPlugin {
    void initialize();
    void start();
    void stop();
    String getId();
    String getName();
    String getVersion();
    boolean isRunning();
    PluginInfo getPluginInfo();
}
```

### AbstractPlugin Class

```java
public abstract class AbstractPlugin implements IPlugin {
    protected final Logger logger;
    protected final AtomicBoolean running;
    protected PluginInfo pluginInfo;

    protected abstract void onInitialize();
    protected abstract void onStart();
    protected abstract void onStop();
}
```

### PluginEvent Class

```java
public class PluginEvent extends Event implements IEvent {
    public static final EventType<PluginEvent> PLUGIN_EVENT;
    public static final EventType<PluginEvent> PLUGIN_INSTALLED;
    public static final EventType<PluginEvent> PLUGIN_UNINSTALLED;
    public static final EventType<PluginEvent> PLUGIN_STARTED;
    public static final EventType<PluginEvent> PLUGIN_STOPPED;
    public static final EventType<PluginEvent> PLUGIN_ERROR;
    public static final EventType<PluginEvent> PLUGIN_LOADED;

    public String getMessage();
    public String getPluginId();
}
```

---

## Support

For questions and issues:
- GitHub Issues: https://github.com/thesophisticateddev/fxdb/issues
- Documentation: Check the `docs/` directory

---

*Last updated: February 2026*
