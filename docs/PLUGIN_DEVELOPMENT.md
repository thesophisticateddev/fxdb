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
10. [Accessing the Database Layer](#accessing-the-database-layer)
11. [Building Plugins with JavaFX UI](#building-plugins-with-javafx-ui)
12. [Plugin Categories](#plugin-categories)
13. [Best Practices](#best-practices)
14. [Walkthrough: Schema Visualizer Plugin](#walkthrough-schema-visualizer-plugin)
15. [Packaging and Distribution](#packaging-and-distribution)
16. [Troubleshooting](#troubleshooting)
17. [API Reference](#api-reference)

---

## Overview

FXDB supports a plugin system that allows developers to extend the application's functionality. Plugins can:

- Add support for new database types (NoSQL, cloud databases)
- Provide syntax highlighting and code completion
- Add data export/import capabilities
- Create custom UI components and themes
- Implement custom tools and utilities
- Visualize database schemas and relationships

**Key Features:**
- Plugins run in isolated threads for stability
- Each plugin JAR is loaded in its own `URLClassLoader` with the application's classloader as parent
- Communication via a JavaFX-based event bus
- Annotation-based configuration with `@FXPlugin`
- Access to `fxdb-core` and `fxdb-db` APIs from the parent classloader (no need to bundle them)

---

## Plugin Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        FXDB Application                          │
│                                                                  │
│   App ClassLoader (fxdb-core, fxdb-db, fxdb-ui, JavaFX)         │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│                        Plugin Manager                            │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐ │
│  │    Plugin A       │  │    Plugin B       │  │   Plugin C     │ │
│  │  URLClassLoader   │  │  URLClassLoader   │  │ URLClassLoader │ │
│  │   (Daemon Thread) │  │   (Daemon Thread) │  │ (Daemon Thread)│ │
│  │   plugin-a.jar    │  │   plugin-b.jar    │  │  plugin-c.jar  │ │
│  └────────┬─────────┘  └────────┬─────────┘  └───────┬────────┘ │
│           │                     │                     │          │
│           └─────────────────────┼─────────────────────┘          │
│                                 ▼                                │
│                          ┌───────────┐                           │
│                          │ Event Bus │                           │
│                          └───────────┘                           │
│                                                                  │
│  ┌───────────────────┐  ┌────────────────────────────────────┐  │
│  │ FXPluginRegistry  │  │ plugin-manifest.json               │  │
│  │ (instance store)  │  │ installed-plugins.json             │  │
│  └───────────────────┘  └────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### Components

| Component | Description |
|-----------|-------------|
| `PluginManager` | Central service that loads, starts, stops, and manages external plugin JARs |
| `IPlugin` | Interface that all plugins must implement |
| `AbstractPlugin` | Base class providing lifecycle management, threading, and logging |
| `FXPluginRegistry` | Thread-safe singleton registry storing plugin instances |
| `FXPluginMicrokernel` | Alternative annotation-driven engine with dependency graph resolution |
| `EventBus` | Pub/sub system for inter-plugin and plugin-to-app communication |

### How Plugin Loading Works

1. `PluginManager` reads `plugins/plugin-manifest.json` to discover available plugins
2. For each installed plugin, it locates the JAR file in the `plugins/` directory
3. A new `URLClassLoader` is created for the JAR, with the application classloader as parent
4. The plugin's `mainClass` is loaded, verified to implement `IPlugin`, and instantiated
5. `plugin.initialize()` is called, then the plugin is registered in `FXPluginRegistry`
6. `plugin.start()` is submitted to a thread pool executor, which runs `onStart()` in a new daemon thread

Because the parent classloader provides `fxdb-core`, `fxdb-db`, and JavaFX, your plugin JAR only needs to contain its own classes and any third-party libraries not already in the application.

---

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- FXDB source code installed to your local Maven repository

### Installing FXDB to Local Maven Repository

Before building plugins, install the FXDB modules so Maven can resolve them:

```bash
# From the fxdb root directory:
mvn install -N                              # Install parent POM
mvn install -pl fxdb-core,fxdb-db -DskipTests   # Install core and db modules
```

### Project Setup

Create a new Maven project for your plugin. The recommended location is `plugins/fxdb-plugin-<name>/` inside the FXDB project, but plugins can live anywhere.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.fxsql.plugins</groupId>
    <artifactId>fxdb-plugin-myplugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <javafx.version>17.0.6</javafx.version>
    </properties>

    <dependencies>
        <!-- FXDB Core: plugin API, event bus, annotations -->
        <dependency>
            <groupId>org.fxsql</groupId>
            <artifactId>fxdb-core</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- FXDB DB: DatabaseConnection, DatabaseManager, TableMetaData -->
        <!-- Only needed if your plugin accesses database connections -->
        <dependency>
            <groupId>org.fxsql</groupId>
            <artifactId>fxdb-db</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- JavaFX: only needed if your plugin creates UI -->
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>${javafx.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <!-- Output JAR directly to the plugins/ directory -->
                    <finalName>fxdb-plugin-myplugin-1.0.0</finalName>
                    <outputDirectory>${project.basedir}/../</outputDirectory>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

> **Important:** All FXDB and JavaFX dependencies must use `<scope>provided</scope>`. These classes are available at runtime from the parent classloader. Bundling them would cause class conflicts.

### Directory Structure

```
plugins/
├── plugin-manifest.json                  # Plugin registry
├── installed-plugins.json                # Persisted install state
├── fxdb-plugin-myplugin-1.0.0.jar       # Your built plugin JAR
└── fxdb-plugin-myplugin/                 # Your plugin source (optional location)
    ├── pom.xml
    └── src/main/java/
        └── com/example/myplugin/
            └── MyPlugin.java
```

---

## Plugin Lifecycle

Plugins follow a defined lifecycle managed by `PluginManager`:

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  AVAILABLE  │────▶│  INSTALLED  │────▶│   LOADING   │────▶│   RUNNING   │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                           │                                       │
                           ▼                                       ▼
                    ┌─────────────┐                         ┌─────────────┐
                    │   DISABLED  │                         │   STOPPED   │
                    └─────────────┘                         │ (INSTALLED) │
                                                            └─────────────┘
                                                                   │
                                                                   ▼
                                                            ┌─────────────┐
                                                            │    ERROR    │
                                                            └─────────────┘
```

| Status | Description |
|--------|-------------|
| `AVAILABLE` | Listed in manifest but not installed |
| `INSTALLED` | Installed on disk but not running |
| `LOADING` | Currently being loaded/started |
| `RUNNING` | Active and executing |
| `DISABLED` | Installed but user-disabled |
| `ERROR` | Failed to load or crashed during execution |

### Lifecycle Method Execution Order

| Step | Method | Thread | Description |
|------|--------|--------|-------------|
| 1 | `initialize()` | PluginManager thread | Sets up `PluginInfo`, calls `onInitialize()` |
| 2 | `start()` | PluginManager thread | Creates daemon thread `"Plugin-<id>"`, calls `onStart()` in it |
| 3 | `onStart()` | Plugin daemon thread | Your main plugin logic runs here |
| 4 | `stop()` | PluginManager thread | Sets `running=false`, calls `onStop()`, interrupts thread, joins (5s timeout) |

---

## Creating Your First Plugin

### Step 1: Implement the Plugin Class

```java
package com.example.myplugin;

import org.fxsql.plugins.AbstractPlugin;
import org.fxsql.plugins.FXPlugin;

@FXPlugin(id = "my-first-plugin")
public class MyFirstPlugin extends AbstractPlugin {

    @Override
    public String getId() {
        return "my-first-plugin";  // Must match @FXPlugin id
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
        // Called once when the plugin is loaded
        // Use for lightweight setup (no heavy I/O)
        logger.info("Plugin initialized!");
    }

    @Override
    protected void onStart() {
        // Called in a separate daemon thread
        // Put your main plugin logic here
        logger.info("Plugin started!");

        // For long-running plugins, use a loop:
        while (isRunning()) {
            // Do periodic work...
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;  // Plugin is being stopped
            }
        }
    }

    @Override
    protected void onStop() {
        // Called when the plugin is being stopped
        // Clean up all resources here
        logger.info("Plugin stopped!");
    }
}
```

### Step 2: Register in the Manifest

Add your plugin entry to `plugins/plugin-manifest.json`:

```json
{
  "id": "my-first-plugin",
  "name": "My First Plugin",
  "version": "1.0.0",
  "description": "A short description of what the plugin does.",
  "author": "Your Name",
  "category": "Tools",
  "mainClass": "com.example.myplugin.MyFirstPlugin",
  "jarFile": "fxdb-plugin-myplugin-1.0.0.jar",
  "enabled": true,
  "installed": false,
  "dependencies": []
}
```

### Step 3: Build and Install

```bash
# Build the plugin JAR
cd plugins/fxdb-plugin-myplugin
mvn clean package

# The JAR is output to plugins/fxdb-plugin-myplugin-1.0.0.jar
# Launch FXDB, open Plugin Manager, and click Install on your plugin
```

### Step 4: Using the `@FXPluginStart` Hook (Optional)

You can annotate methods with `@FXPluginStart` for additional startup logic that runs after instantiation but within the microkernel execution flow:

```java
import org.fxsql.plugins.pluginHook.FXPluginStart;

@FXPlugin(id = "my-plugin")
public class MyPlugin extends AbstractPlugin {

    @FXPluginStart
    public void onPluginStart() {
        // Called by FXPluginMicrokernel after the plugin is instantiated
        // and before the full start() lifecycle
        logger.info("Plugin starting via @FXPluginStart hook");
    }

    // ... implement abstract methods
}
```

---

## Plugin Manifest

### Manifest Locations

- **Source (bundled):** `fxdb-ui/src/main/resources/plugin-manifest.json`
- **Runtime:** `plugins/plugin-manifest.json` (takes priority)

`PluginManager` first tries the runtime file, then falls back to the classpath resource.

### Manifest Structure

```json
{
  "manifestVersion": "1.0.0",
  "lastUpdated": "2026-02-02T00:00:00",
  "plugins": [
    {
      "id": "my-plugin",
      "name": "My Plugin",
      "version": "1.0.0",
      "description": "Detailed description for the Plugin Manager UI.",
      "author": "Your Name",
      "category": "Tools",
      "mainClass": "com.example.MyPlugin",
      "jarFile": "fxdb-plugin-myplugin-1.0.0.jar",
      "enabled": true,
      "installed": false,
      "dependencies": []
    }
  ]
}
```

### Field Reference

| Field | Required | Description |
|-------|----------|-------------|
| `id` | Yes | Unique identifier (lowercase with hyphens, e.g., `"schema-visualizer"`) |
| `name` | Yes | Human-readable display name |
| `version` | Yes | Semantic version string (e.g., `"1.0.0"`) |
| `description` | Yes | Detailed description shown in the Plugin Manager UI |
| `author` | Yes | Plugin author name |
| `category` | Yes | One of: `Database`, `Editor`, `Tools`, `Themes` |
| `mainClass` | Yes | Fully qualified class name of your plugin class |
| `jarFile` | Yes | JAR filename located in the `plugins/` directory |
| `enabled` | No | Whether the plugin auto-starts on load (default: `true`) |
| `installed` | No | Installation state managed by `PluginManager` (default: `false`) |
| `dependencies` | No | Array of plugin IDs that must be loaded first |

> **Important:** The `id` in the manifest must match both the `@FXPlugin(id = "...")` annotation and the value returned by `getId()`.

---

## Threading Model

Plugins run in dedicated daemon threads. Understanding the threading model is critical for writing stable plugins.

### How It Works

- `AbstractPlugin.start()` creates a new `Thread` named `"Plugin-<id>"` and sets it as a daemon thread
- Your `onStart()` method executes inside this thread
- `PluginManager` also has a `CachedThreadPool` executor for submitting `plugin.start()` calls
- `stop()` sets `running` to `false`, calls `onStop()`, interrupts the thread, then joins with a 5-second timeout

### Rules

**1. Never block the JavaFX Application Thread**

```java
// WRONG - infinite work on FX thread
@Override
protected void onStart() {
    Platform.runLater(() -> {
        while (true) { /* blocks UI */ }
    });
}

// CORRECT - do work in the plugin thread, update UI briefly via Platform.runLater
@Override
protected void onStart() {
    while (isRunning()) {
        String result = doExpensiveWork();
        Platform.runLater(() -> statusLabel.setText(result));
        Thread.sleep(1000);
    }
}
```

**2. Use `Platform.runLater()` for all JavaFX UI operations**

JavaFX nodes can only be accessed from the FX Application Thread:

```java
Platform.runLater(() -> {
    stage.show();
    someLabel.setText("Updated!");
});
```

**3. Check `isRunning()` in work loops**

```java
@Override
protected void onStart() {
    while (isRunning()) {
        doWork();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            break;  // Thread was interrupted during stop()
        }
    }
}
```

**4. Use thread-safe collections for shared state**

```java
private final Map<String, Object> cache = new ConcurrentHashMap<>();
private final BlockingQueue<Command> commandQueue = new LinkedBlockingQueue<>();
```

---

## Event System

Plugins communicate with the application and other plugins via the `EventBus`, which is a static pub/sub system built on JavaFX event types.

### Firing Events

```java
import org.fxsql.events.EventBus;
import org.fxsql.plugins.events.PluginEvent;

// Fire a general plugin event
EventBus.fireEvent(new PluginEvent(
    PluginEvent.PLUGIN_EVENT,
    "Something happened in my plugin",
    getId()
));

// Fire a specific event type
EventBus.fireEvent(new PluginEvent(
    PluginEvent.PLUGIN_ERROR,
    "Connection failed: timeout",
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
    EventBus.addEventHandler(PluginEvent.PLUGIN_EVENT, event -> {
        logger.info("Received plugin event: " + event.getMessage()
            + " from: " + event.getPluginId());
    });
}
```

### Available Event Types

| Event Type | Fired When |
|------------|------------|
| `PluginEvent.PLUGIN_EVENT` | Base type; catches all plugin events |
| `PluginEvent.PLUGIN_INSTALLED` | A plugin was installed |
| `PluginEvent.PLUGIN_UNINSTALLED` | A plugin was uninstalled |
| `PluginEvent.PLUGIN_STARTED` | A plugin started successfully (fired by `AbstractPlugin`) |
| `PluginEvent.PLUGIN_STOPPED` | A plugin was stopped (fired by `AbstractPlugin`) |
| `PluginEvent.PLUGIN_ERROR` | A plugin encountered an error (fired by `AbstractPlugin`) |
| `PluginEvent.PLUGIN_LOADED` | A plugin was loaded into memory |

### Creating Custom Events

```java
import javafx.event.Event;
import javafx.event.EventType;
import org.fxsql.events.IEvent;

public class SchemaRefreshEvent extends Event implements IEvent {

    public static final EventType<SchemaRefreshEvent> SCHEMA_REFRESHED =
        new EventType<>(Event.ANY, "SCHEMA_REFRESHED");

    private final String connectionName;

    public SchemaRefreshEvent(String connectionName) {
        super(SCHEMA_REFRESHED);
        this.connectionName = connectionName;
    }

    @Override
    public String getMessage() {
        return "Schema refreshed for: " + connectionName;
    }

    public String getConnectionName() {
        return connectionName;
    }
}
```

---

## Dependency Injection

The `FXPluginMicrokernel` provides a lightweight dependency injection system for plugins that need shared services.

### Declaring Dependencies on Constructor Parameters

Use `@FXPluginDependency` on constructor parameters. The plugin class must have exactly one constructor:

```java
import org.fxsql.plugins.plugindepdency.FXPluginDependency;

@FXPlugin(id = "dependent-plugin")
public class DependentPlugin extends AbstractPlugin {

    private final SomeService service;

    public DependentPlugin(
            @FXPluginDependency(getName = "someService") SomeService service) {
        this.service = service;
    }

    // ... implement abstract methods
}
```

The `getName` value must match the method name in the dependency factory, or the simple class name of a registered instance in the `FXPluginRegistry`.

### Creating Dependency Factories

Annotate a class with `@FXPluginDependencyFactory` and its provider methods with `@FXDependencyInstance`:

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

Each `@FXDependencyInstance` method:
- Must take no parameters
- Is called once; the return value is registered in `FXPluginRegistry` under the method name
- Produces a singleton instance shared across all plugins

### Using FXPluginRegistry Directly

You can also access the registry directly to look up instances:

```java
import org.fxsql.plugins.runtime.FXPluginRegistry;

Object service = FXPluginRegistry.INSTANCE.get("someService");

// Type-safe lookup
SomeService typed = FXPluginRegistry.INSTANCE.get("someService", SomeService.class);
```

---

## Accessing the Database Layer

Plugins can access database connections and metadata through the `fxdb-db` module. Add `fxdb-db` as a `provided` dependency in your `pom.xml`.

### Key Classes

| Class | Package | Description |
|-------|---------|-------------|
| `DatabaseManager` | `org.fxsql` | Manages all database connections; stores and retrieves them by name |
| `DatabaseConnection` | `org.fxsql` | Interface for database connections; provides JDBC access and metadata |
| `TableMetaData` | `org.fxsql.model` | Metadata for a table: columns, primary keys, foreign keys, indexes |
| `TableMetaData.ColumnInfo` | `org.fxsql.model` | Column metadata: name, type, size, nullable, auto-increment |
| `TableMetaData.PrimaryKeyInfo` | `org.fxsql.model` | Primary key column info |
| `TableMetaData.ForeignKeyInfo` | `org.fxsql.model` | Foreign key info including referenced table/column |
| `TableMetaData.IndexInfo` | `org.fxsql.model` | Index metadata |

### Getting a Database Connection

```java
import org.fxsql.DatabaseManager;
import org.fxsql.DatabaseConnection;

DatabaseManager dbManager = new DatabaseManager();
dbManager.loadStoredConnections();

// List all connection names
Set<String> names = dbManager.getConnectionList();

// Get an existing connection
DatabaseConnection dbConn = dbManager.getConnection("my-connection");

// If not connected, reconnect from stored metadata
if (dbConn == null || !dbConn.isConnected()) {
    dbConn = dbManager.connectByConnectionName("my-connection");
}

// Get the raw JDBC connection
java.sql.Connection jdbcConn = dbConn.getConnection();
```

### Reading Table Metadata

```java
import org.fxsql.model.TableMetaData;

// Get table names
List<String> tables = dbConn.getTableNames();

// Get detailed metadata for a specific table
TableMetaData meta = dbConn.getTableMetaData("users");

// Columns
for (TableMetaData.ColumnInfo col : meta.getColumns()) {
    String name = col.getName();
    String type = col.getFormattedType();  // e.g., "VARCHAR(255)"
    boolean nullable = col.isNullable();
    boolean autoIncrement = col.isAutoIncrement();
}

// Primary keys
for (TableMetaData.PrimaryKeyInfo pk : meta.getPrimaryKeys()) {
    String columnName = pk.getColumnName();
    String pkName = pk.getPkName();
}

// Foreign keys
for (TableMetaData.ForeignKeyInfo fk : meta.getForeignKeys()) {
    String fkColumn = fk.getFkColumnName();
    String referencedTable = fk.getPkTableName();
    String referencedColumn = fk.getPkColumnName();
    String description = fk.getReferenceDescription();  // "fkCol -> pkTable(pkCol)"
}

// Indexes
for (TableMetaData.IndexInfo idx : meta.getIndexes()) {
    String indexName = idx.getIndexName();
    boolean unique = !idx.isNonUnique();
}
```

### Using JDBC DatabaseMetaData Directly

For schema-wide discovery (e.g., listing all tables), you can use JDBC `DatabaseMetaData`:

```java
java.sql.Connection conn = dbConn.getConnection();
java.sql.DatabaseMetaData dbMeta = conn.getMetaData();

// Discover all tables
try (ResultSet rs = dbMeta.getTables(null, null, "%", new String[]{"TABLE"})) {
    while (rs.next()) {
        String tableName = rs.getString("TABLE_NAME");
    }
}
```

---

## Building Plugins with JavaFX UI

Plugins can create their own JavaFX windows. Since `onStart()` runs in a plugin daemon thread, you must use `Platform.runLater()` to create and show stages.

### Opening a Stage from a Plugin

```java
@FXPlugin(id = "my-ui-plugin")
public class MyUIPlugin extends AbstractPlugin {

    private Stage stage;

    @Override
    protected void onStart() {
        Platform.runLater(() -> {
            stage = new Stage();
            stage.setTitle("My Plugin Window");

            BorderPane root = new BorderPane();
            root.setCenter(new Label("Hello from plugin!"));

            stage.setScene(new Scene(root, 600, 400));
            stage.show();
        });
    }

    @Override
    protected void onStop() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
                stage = null;
            }
        });
    }

    // ... getId(), getName(), getVersion(), onInitialize()
}
```

### JavaFX Dependencies

If your plugin uses JavaFX controls, add them as `provided` dependencies:

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>17.0.6</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-graphics</artifactId>
    <version>17.0.6</version>
    <scope>provided</scope>
</dependency>
```

For image export features using `SwingFXUtils`, also add:

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-swing</artifactId>
    <version>17.0.6</version>
    <scope>provided</scope>
</dependency>
```

---

## Plugin Categories

Plugins are organized into categories for the Plugin Manager UI:

### Database

Plugins that add support for new database types or connection methods.

```json
{ "category": "Database" }
```

Examples: MongoDB Connector, Redis Connector, Cassandra Connector

### Editor

Plugins that enhance the SQL editor experience.

```json
{ "category": "Editor" }
```

Examples: SQL Syntax Highlighter, code completion, query templates

### Tools

Utility plugins for data manipulation, visualization, and productivity.

```json
{ "category": "Tools" }
```

Examples: Schema Visualizer, Data Export, Query History Manager

### Themes

Visual customization plugins.

```json
{ "category": "Themes" }
```

Examples: Dark Theme Pack, custom color schemes

---

## Best Practices

### Resource Management

Always clean up resources in `onStop()`:

```java
@Override
protected void onStop() {
    if (connection != null) {
        connection.close();
    }
    if (executor != null) {
        executor.shutdown();
    }
    cache.clear();
}
```

### Error Handling

Catch exceptions in `onStart()` and report them via the event bus:

```java
@Override
protected void onStart() {
    try {
        riskyOperation();
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Operation failed", e);
        EventBus.fireEvent(new PluginEvent(
            PluginEvent.PLUGIN_ERROR,
            "Error: " + e.getMessage(),
            getId()
        ));
    }
}
```

> Note: If `onStart()` throws an uncaught exception, `AbstractPlugin` catches it, sets status to `ERROR`, and fires `PLUGIN_ERROR` automatically.

### Configuration Files

Store plugin configuration in the `plugins/` directory:

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

### Logging

`AbstractPlugin` provides a `Logger` instance named after your class:

```java
logger.info("Information message");
logger.warning("Warning message");
logger.severe("Error message");
logger.fine("Debug message");
```

### Graceful Shutdown with Command Queues

For plugins that process commands asynchronously, use a `BlockingQueue` with a poison-pill pattern:

```java
private final BlockingQueue<Command> commandQueue = new LinkedBlockingQueue<>();
private volatile boolean workerRunning = false;

@Override
protected void onStart() {
    workerRunning = true;
    while (workerRunning) {
        try {
            Command cmd = commandQueue.poll(1, TimeUnit.SECONDS);
            if (cmd != null) processCommand(cmd);
        } catch (InterruptedException e) {
            break;
        }
    }
}

@Override
protected void onStop() {
    workerRunning = false;
    // Poison pill to unblock the worker
    commandQueue.offer(new Command(Command.Type.SHUTDOWN, null));
}
```

---

## Walkthrough: Schema Visualizer Plugin

This section walks through the complete Schema Visualizer plugin as a real-world example. The full source is at `plugins/fxdb-plugin-visualizer/`.

### Project Structure

```
plugins/fxdb-plugin-visualizer/
├── pom.xml
└── src/main/java/org/fxsql/plugins/visualize/
    ├── SchemaVisualizerPlugin.java    # Plugin entry point
    ├── SchemaVisualizerStage.java     # JavaFX Stage with toolbar
    └── SchemaCanvas.java             # Canvas-based ER diagram renderer
```

### 1. The Plugin Class

The plugin extends `AbstractPlugin` and opens a JavaFX window on start:

```java
@FXPlugin(id = "schema-visualizer")
public class SchemaVisualizerPlugin extends AbstractPlugin {

    private SchemaVisualizerStage stage;

    @Override
    public String getId() { return "schema-visualizer"; }

    @Override
    public String getName() { return "Schema Visualizer"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    protected void onInitialize() {
        logger.info("Schema Visualizer plugin initialized");
    }

    @Override
    protected void onStart() {
        // Must create JavaFX nodes on the FX Application Thread
        Platform.runLater(() -> {
            stage = new SchemaVisualizerStage();
            stage.show();
        });
    }

    @Override
    protected void onStop() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
                stage = null;
            }
        });
    }
}
```

### 2. Accessing Database Connections

The `SchemaVisualizerStage` uses `DatabaseManager` to discover connections and `DatabaseConnection.getConnection()` to get the raw JDBC connection for metadata queries:

```java
DatabaseManager dbManager = new DatabaseManager();
dbManager.loadStoredConnections();

// Populate a ComboBox with connection names
Set<String> names = dbManager.getConnectionList();

// When user selects a connection, get the JDBC connection
DatabaseConnection dbConn = dbManager.getConnection(selectedName);
if (dbConn == null || !dbConn.isConnected()) {
    dbConn = dbManager.connectByConnectionName(selectedName);
}
java.sql.Connection jdbcConn = dbConn.getConnection();
```

### 3. Rendering with JavaFX Canvas

The `SchemaCanvas` uses JDBC `DatabaseMetaData` to discover tables and relationships, then draws them on a `Canvas`:

- Tables are rendered as rounded rectangles with a colored header and column rows
- Primary keys are marked with a gold "PK" badge, foreign keys with a blue "FK" badge
- Relationship lines are drawn as dashed bezier curves between FK-connected tables
- Mouse drag pans the view, scroll wheel zooms

### 4. Manifest Entry

```json
{
  "id": "schema-visualizer",
  "name": "Schema Visualizer",
  "version": "1.0.0",
  "description": "Generate ER diagrams and visualize database schema relationships.",
  "author": "FXDB Team",
  "category": "Tools",
  "mainClass": "org.fxsql.plugins.visualize.SchemaVisualizerPlugin",
  "jarFile": "fxdb-plugin-visualizer-1.0.0.jar",
  "enabled": true,
  "installed": false,
  "dependencies": []
}
```

### 5. Building

```bash
cd plugins/fxdb-plugin-visualizer
mvn clean package
# Produces: plugins/fxdb-plugin-visualizer-1.0.0.jar
```

---

## Packaging and Distribution

### Building Your Plugin

```bash
cd plugins/fxdb-plugin-myplugin
mvn clean package
```

If your `pom.xml` configures `outputDirectory` to `${project.basedir}/../`, the JAR goes directly to the `plugins/` directory.

### Including Third-Party Dependencies

If your plugin uses libraries not already in FXDB, you need to shade them into your JAR:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.1</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
        </execution>
    </executions>
</plugin>
```

### Installation Methods

**Via Plugin Manager UI:**
1. Open FXDB and navigate to the Plugin Manager
2. Find your plugin in the list (it appears if registered in the manifest)
3. Click "Install" — if the JAR isn't found, a dialog offers to browse for it
4. Click "Start" to activate the plugin

**Manual installation:**
1. Copy your JAR to the `plugins/` directory
2. Add an entry to `plugins/plugin-manifest.json`
3. Restart FXDB, or use Plugin Manager to install and start

### Distribution Checklist

- [ ] JAR file built and tested
- [ ] Manifest entry with accurate `mainClass` and `jarFile`
- [ ] Plugin `id` is consistent across `@FXPlugin`, `getId()`, and manifest
- [ ] All non-FXDB dependencies are shaded into the JAR (or documented)
- [ ] `onStop()` cleans up all resources (threads, connections, UI)
- [ ] Version follows semantic versioning
- [ ] README with installation instructions (for standalone distribution)

---

## Troubleshooting

### Plugin Won't Load

1. **JAR not found:** Verify the JAR file exists in `plugins/` and the filename matches `jarFile` in the manifest
2. **Class not found:** Check that `mainClass` in the manifest matches your fully qualified class name exactly
3. **Missing IPlugin:** Ensure your class extends `AbstractPlugin` or implements `IPlugin` directly
4. **Dependency error:** Make sure `fxdb-core` and `fxdb-db` are installed in your local Maven repo (`mvn install` from the fxdb root)

### Plugin Crashes on Start

1. Check for null pointer exceptions in `onInitialize()` or `onStart()`
2. If creating JavaFX UI, ensure you use `Platform.runLater()`
3. Verify all `provided` dependencies are actually available at runtime
4. Check the application logs — `AbstractPlugin` catches and logs exceptions from `onStart()`

### Events Not Received

1. Register handlers in `onInitialize()`, not `onStart()` — handlers must be registered before events fire
2. Ensure event types match: `PluginEvent.PLUGIN_EVENT` catches all subtypes, specific types like `PLUGIN_STARTED` only catch that type
3. Verify `EventBus` is imported from `org.fxsql.events`, not another package

### UI Updates Not Showing

1. All JavaFX node operations must go through `Platform.runLater()`
2. Check that node references aren't null (stage may not be created yet)
3. For Canvas rendering, call `redraw()` after data changes

### Plugin Won't Stop

1. Ensure `onStop()` signals any work loops to exit (set flags, offer poison pills)
2. `AbstractPlugin.stop()` interrupts the plugin thread after calling `onStop()`, so `Thread.sleep()` and `BlockingQueue.poll()` calls will throw `InterruptedException`
3. The framework waits 5 seconds for the thread to finish before giving up

### ClassLoader Issues

1. Don't use `Class.forName()` without specifying the classloader — use `Thread.currentThread().getContextClassLoader()` or pass the plugin's classloader
2. Classes from `fxdb-core` and `fxdb-db` are loaded by the parent classloader; your plugin classes are loaded by the `URLClassLoader`. They can see each other but other plugins' classes are isolated

---

## API Reference

### IPlugin Interface

```java
public interface IPlugin {
    void initialize();       // Called once when loaded
    void start();            // Called to start the plugin
    void stop();             // Called to stop the plugin
    String getId();          // Unique identifier
    String getName();        // Display name
    String getVersion();     // Version string
    boolean isRunning();     // Current running state
    PluginInfo getPluginInfo();  // Plugin metadata
}
```

### AbstractPlugin Class

```java
public abstract class AbstractPlugin implements IPlugin {
    protected final Logger logger;              // Logger named after your class
    protected final AtomicBoolean running;      // Thread-safe running state
    protected PluginInfo pluginInfo;            // Populated during initialize()
    protected Thread pluginThread;              // The daemon thread running onStart()

    protected abstract void onInitialize();     // Your initialization logic
    protected abstract void onStart();          // Your main logic (runs in daemon thread)
    protected abstract void onStop();           // Your cleanup logic
}
```

### Annotations

| Annotation | Target | Description |
|------------|--------|-------------|
| `@FXPlugin(id = "...")` | Class | Marks a class as a plugin with a unique identifier |
| `@FXPluginStart` | Method | Marks a method to be called during microkernel startup |
| `@FXPluginDependency(getName = "...")` | Constructor parameter | Injects a dependency by name from the registry |
| `@FXPluginDependencyFactory` | Class | Marks a class as a dependency provider |
| `@FXDependencyInstance` | Method | Marks a method that produces a dependency instance |
| `@FXPluginHandleEvent` | Method | Marks a method as an event handler (must accept one `Object` parameter) |

### PluginEvent Class

```java
public class PluginEvent extends Event implements IEvent {
    // Event types (hierarchical — PLUGIN_EVENT catches all subtypes)
    public static final EventType<PluginEvent> PLUGIN_EVENT;
    public static final EventType<PluginEvent> PLUGIN_INSTALLED;
    public static final EventType<PluginEvent> PLUGIN_UNINSTALLED;
    public static final EventType<PluginEvent> PLUGIN_STARTED;
    public static final EventType<PluginEvent> PLUGIN_STOPPED;
    public static final EventType<PluginEvent> PLUGIN_ERROR;
    public static final EventType<PluginEvent> PLUGIN_LOADED;

    public PluginEvent(EventType<PluginEvent> eventType, String message, String pluginId);
    public String getMessage();
    public String getPluginId();
}
```

### FXPluginRegistry

```java
public class FXPluginRegistry {
    public static final FXPluginRegistry INSTANCE;

    void addInstance(String key, Object value);
    Object get(String key);
    <T> T get(String key, Class<T> type);
    boolean exists(String key);
    Object remove(String key);
    Set<String> getKeys();
    int size();
    void clear();
}
```

### PluginInfo.PluginStatus Enum

```java
public enum PluginStatus {
    AVAILABLE,   // In manifest, not yet installed
    INSTALLED,   // Installed but not running
    RUNNING,     // Currently active
    DISABLED,    // Installed but disabled by user
    ERROR,       // Failed to load or run
    LOADING      // Currently being loaded
}
```

---

*Last updated: February 2026*
