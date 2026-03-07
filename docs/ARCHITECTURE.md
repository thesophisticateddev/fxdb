# FXDB Architecture Overview

## Project Structure

FXDB is a JavaFX-based database client built as a Maven multi-module project targeting Java 17.

```
fxdb/
├── fxdb-core/       Core utilities, plugin system, events, encryption
├── fxdb-db/         Database connectivity, JDBC driver loading, connection management
├── fxdb-ui/         JavaFX application, controllers, components, FXML resources
└── plugins/         External plugin JARs (each is a separate Maven project)
    ├── fxdb-plugin-mongodb/
    └── fxdb-plugin-visualizer/
```

### Module Dependencies

```
fxdb-ui  ──depends on──►  fxdb-db  ──depends on──►  fxdb-core
```

- **fxdb-core** has no internal dependencies — it provides shared infrastructure.
- **fxdb-db** depends on fxdb-core for events, encryption, and AppPaths.
- **fxdb-ui** depends on both fxdb-db and fxdb-core.
- **Plugins** depend on fxdb-core (provided scope) and are loaded at runtime via isolated classloaders.

---

## Dependency Injection

The application uses **Google Guice 7.0.0** for dependency injection. A single module configures all singletons:

```
DatabaseModule
├── DatabaseManager      (eager singleton)
├── DriverDownloader     (eager singleton)
├── JDBCDriverLoader     (eager singleton)
├── WindowManager        (eager singleton)
└── PluginManager        (eager singleton)
```

The Guice injector is created in `MainApplication.init()` and wired into JavaFX's FXML loader so that all `@FXML` controllers are instantiated through Guice, enabling `@Inject` fields in any controller.

---

## Application Startup

```
Launcher.main()
  └── MainApplication.init()
        ├── Create Guice Injector (DatabaseModule)
        └── start(Stage)
              ├── Load main.fxml via WindowManager
              ├── Apply AtlantaFX PrimerLight theme
              ├── Set application icon
              └── MainController.initialize()
                    ├── Setup table browser TreeView
                    ├── Setup plugin browser TreeView (hidden)
                    ├── Register PluginUIContext in FXPluginRegistry
                    ├── Register DatabaseManager in FXPluginRegistry
                    ├── Load stored connections
                    ├── Load JDBC drivers asynchronously
                    ├── Start enabled plugins
                    └── Restore workspace state
```

`Launcher` exists as a non-JavaFX entry point required by the Maven Shade plugin to correctly package the fat JAR.

---

## Database Layer

### Connection Hierarchy

```
DatabaseConnection (interface)
└── AbstractDatabaseConnection
    ├── SqliteConnection          SQLite file-based connections
    ├── MySqlConnection           MySQL protocol connections
    ├── PostgresSqlConnection     PostgreSQL connections
    └── GenericJdbcConnection     Fallback for any JDBC-compatible database
```

`DatabaseConnectionFactory` selects the appropriate implementation based on the database vendor string.

### DatabaseManager

The central singleton managing all active connections:

- Stores connection metadata (host, port, credentials, JDBC URL) in `connection_store.json`
- Encrypts passwords using AES-GCM via `EncryptionUtil`
- Tracks which connection is currently active
- Exposes the underlying `java.sql.Connection` for query execution

### Dynamic JDBC Driver Loading

```
JDBCDriverLoader                       DriverDownloader
├── Scans dynamic-jars/ on startup     ├── Manages driver_repository.json
├── Loads JARs via URLClassLoader      ├── Downloads JARs from configured URLs
├── Registers drivers with DriverShim  └── Fires DriverDownloadEvent on completion
└── Fires DriverLoadedEvent
```

`DynamicJDBCDriverLoader` handles the low-level classloader mechanics. `DriverShim` wraps dynamically loaded drivers so they can be registered with `java.sql.DriverManager` (which normally only recognizes drivers from the system classloader).

---

## Event System

A lightweight publish/subscribe bus built on JavaFX's `EventType` hierarchy:

```java
EventBus.fireEvent(new PluginEvent(PluginEvent.PLUGIN_STARTED, message, pluginId));
EventBus.addEventHandler(PluginEvent.PLUGIN_STARTED, event -> { ... });
```

### Event Types

| Event                    | Fired When                                |
|--------------------------|-------------------------------------------|
| `PluginEvent`            | Plugin installed/uninstalled/started/stopped/loaded/error |
| `DriverDownloadEvent`    | JDBC driver download completes            |
| `DriverLoadedEvent`      | JDBC driver loaded into classloader       |
| `NewConnectionAddedEvent`| Database connection added or modified      |

Listeners in the UI layer (`DriverEventListener`, `NewConnectionAddedListener`) react to these events and update the interface on the JavaFX Application Thread via `Platform.runLater()`.

---

## Plugin System

### Architecture

Plugins are external JARs that implement the `IPlugin` interface. Each runs in an isolated `URLClassLoader` and communicates with the host application through `FXPluginRegistry`.

```
plugin-manifest.json          Declares available plugins (name, JAR, main class)
        │
        ▼
  PluginManager               Discovers, loads, and manages plugin lifecycle
        │
        ├── URLClassLoader     Each plugin gets its own classloader
        │
        ├── FXPluginRegistry   Thread-safe ConcurrentHashMap for sharing instances
        │   ├── "ui.context"       → PluginUIContext (TabPane + TreeView access)
        │   └── "databaseManager"  → DatabaseManager (shared DB connections)
        │
        └── ExecutorService    Plugins start in daemon threads
```

### Plugin Lifecycle

```
AVAILABLE ──install──► INSTALLED ──start──► LOADING ──► RUNNING
    ▲                      │                               │
    └──uninstall───────────┘◄──────────stop────────────────┘
                           │
                         ERROR  (on failure at any stage)
```

### Plugin Interface

```java
public interface IPlugin {
    void initialize();       // Called after classloading, before start
    void start();            // Begin plugin operation
    void stop();             // Cleanup and shutdown
    String getId();
    String getName();
    String getVersion();
    boolean isRunning();
}
```

`AbstractPlugin` provides a default implementation with thread management, `AtomicBoolean` state tracking, and EventBus integration. Concrete plugins override `onInitialize()`, `onStart()`, and `onStop()`.

### Plugin UI Integration

`PluginUIContext` gives plugins controlled access to the main UI:

- **TabPane** — plugins can add/remove tabs (e.g., document viewers, query results)
- **Plugin Browser TreeView** — a dedicated TreeView in the left panel, hidden until a plugin adds a node. Automatically shows/hides based on whether any plugin nodes exist.

This separation keeps SQL database objects in the main tree and NoSQL/plugin objects in the plugin tree.

### Existing Plugins

| Plugin | Purpose |
|--------|---------|
| **Schema Visualizer** | Canvas-based ER diagram of database tables, columns, and foreign keys |
| **MongoDB** | NoSQL browser — connect to MongoDB, browse databases/collections, view documents |

---

## UI Layer

### Main Window Layout

```
┌──────────────────────────────────────────────────────────┐
│  Menu Bar (AppMenuBar)                                   │
├────────────────┬─────────────────────────────────────────┤
│ Left Panel     │  Right Panel (TabPane)                  │
│                │                                         │
│ ┌────────────┐ │  ┌──────────────────────────────────┐   │
│ │ Connection │ │  │ Tab: Query Editor (SQLScriptPane) │   │
│ │ Selector   │ │  │ Tab: Results (EditableTablePane)  │   │
│ ├────────────┤ │  │ Tab: Table Info (TableInfoPane)   │   │
│ │ Database   │ │  │ Tab: Plugin Tabs                  │   │
│ │ Browser    │ │  └──────────────────────────────────┘   │
│ │ (TreeView) │ │                                         │
│ ├────────────┤ │                                         │
│ │ Plugin     │ │                                         │
│ │ Browser    │ │                                         │
│ │ (hidden)   │ │                                         │
│ └────────────┘ │                                         │
├────────────────┴─────────────────────────────────────────┤
│  Status Bar / Notifications                              │
└──────────────────────────────────────────────────────────┘
```

### Key UI Components

| Component | Purpose |
|-----------|---------|
| `MainController` | Central controller — connection management, tree browsing, query dispatch |
| `EditableTablePane` | In-place editing of table rows with add/delete support |
| `SQLScriptPane` | SQL editor with execution support |
| `ResultTablePagination` | Paginated display for large result sets |
| `TableInfoPane` | Column, constraint, and index metadata display |
| `ToastNotification` | Non-blocking notification popups |
| `ConnectionStatusIndicator` | Visual connection state feedback |

### Dialog Controllers

Each dialog has a dedicated FXML file and controller:

| Controller | Dialog |
|------------|--------|
| `NewConnectionController` | Add/edit database connections |
| `CreateTableController` | Create new tables |
| `AddColumnController` | Add columns to existing tables |
| `CreateTriggerController` | Create database triggers |
| `AddForeignKeyController` | Define foreign key constraints |
| `InstallDriverController` | Install JDBC drivers |
| `PluginManagerController` | Browse, install, enable/disable plugins |

---

## Data Persistence

### Application Data Directory

Resolved by `AppPaths` based on OS:

| Platform | Path | Dev Mode (`-Dfxdb.dev=true`) |
|----------|------|------------------------------|
| Windows  | `%APPDATA%/fxdb/` | Current working directory |
| macOS    | `~/Library/Application Support/fxdb/` | Current working directory |
| Linux    | `~/.fxdb/` | Current working directory |

### File Layout

```
{AppPaths}/
├── META-DATA/
│   ├── connection_store.json     Saved database connections
│   ├── driver_repository.json    JDBC driver download references
│   ├── .encryption.key           AES-GCM encryption key
│   └── workspaces/               Workspace state files
└── plugins/
    └── installed-plugins.json    Plugin installation state

{install-dir}/
├── plugins/
│   ├── plugin-manifest.json      Plugin registry (shipped with app)
│   └── *.jar                     Plugin JAR files
└── dynamic-jars/                 Downloaded JDBC driver JARs
```

Config and user data go to the user-writable AppPaths directory. Binary files (plugin JARs, JDBC driver JARs) stay relative to the installation directory to avoid requiring users to re-download after updates.

### Encryption

Passwords are encrypted at rest using AES-128-GCM. The encryption key is stored in `META-DATA/.encryption.key` and generated on first use. Each encrypted value has a unique IV prepended (Base64-encoded).

---

## Build & Packaging

### Development

```bash
# Run from IDE or command line
mvn javafx:run -pl fxdb-ui

# With dev mode (uses working directory for data)
mvn javafx:run -pl fxdb-ui -Dfxdb.dev=true

# Build a plugin (in its own repository)
# e.g. cd ../fxdb-mongodb-plugin && mvn clean package
```

### Production Build

```bash
# Create shaded (fat) JAR
mvn clean package -pl fxdb-ui

# Create native installer (auto-detects platform)
mvn jpackage:jpackage -pl fxdb-ui
```

### Native Installers

The `jpackage-maven-plugin` creates platform-specific installers with auto-activating profiles:

| Platform | Installer | Icon Format |
|----------|-----------|-------------|
| Windows  | MSI       | `.ico`      |
| macOS    | DMG       | `.icns`     |
| Linux    | DEB       | `.png`      |

### Plugin Build

Plugins are built as shaded JARs with fxdb-core as a provided dependency:

```xml
<dependency>
    <groupId>org.fxsql</groupId>
    <artifactId>fxdb-core</artifactId>
    <scope>provided</scope>
</dependency>
```

This keeps plugin JARs small since core classes are already available in the host application's classloader (the plugin's `URLClassLoader` parent).

---

## Key Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| JavaFX  | 17.0.6  | UI framework |
| Google Guice | 7.0.0 | Dependency injection |
| Jackson | 2.16.0 | JSON serialization |
| AtlantaFX | 2.0.1 | Modern UI theme (PrimerLight) |
| TableSaw | 0.43.1 | Data analysis/visualization |
| MongoDB Driver | 4.11.1 | MongoDB connectivity (plugin) |
