# DockFX Integration

FXDB uses [DockFX](https://github.com/RobertBColton/DockFX) for its dockable panel layout via a maintained fork: [`dockfx-fxdb`](https://github.com/thesophisticateddev/dockfx-fxdb).

## Fork Details

The upstream DockFX targets Java 8 and is unmaintained. Our fork (`org.fxsql:dockfx-fxdb:1.0.0`) applies:

- **Java 17+ compilation** with `org.openjfx` dependencies
- **JPMS module** (`org.fxsql.dockfx`)
- **Removal of `com.sun.*` internal APIs** (`InputEventUtils`, `StyleManager`)

All fork changes are documented in `dockfx-fxdb/CHANGES-FROM-UPSTREAM.md`.

## Dependency Resolution

The fork jar is committed to `fxdb/libs/` as a file-based Maven repository — no external hosting or `mvn install` required. Maven resolves it automatically via the `local-libs` repository in the parent `pom.xml`.

## Architecture

The main UI uses a `DockPane` as its root layout container. Each major panel is wrapped in a `DockNode`:

| Class | Panel | Default Position |
|---|---|---|
| `ConnectionDockNode` | Database selector + schema tree | `LEFT` |
| `WorkspaceDockNode` | TabPane (editors, table data, about) | `RIGHT` |

These are created programmatically in `MainController.initializeDockLayout()` and docked into a `DockPane` hosted inside the FXML `dockContainer`.

## Styling

DockFX's default CSS is loaded in `MainApplication.java`. Theme overrides live in `stylesheets/dock-theme.css`, targeting selectors like `.dock-title-bar`, `.dock-node`, and `.dock-area-indicator` to match the AtlantaFX PrimerLight theme.

## Updating the Fork Jar

```bash
cd dockfx-fxdb
mvn clean package

mvn deploy:deploy-file \
  -DgroupId=org.fxsql -DartifactId=dockfx-fxdb -Dversion=<NEW_VERSION> \
  -Dpackaging=jar -Dfile=target/dockfx-fxdb-<NEW_VERSION>.jar \
  -DpomFile=pom.xml -Durl=file:///<path-to-fxdb>/libs \
  -DrepositoryId=local-libs
```

Then update the version in `fxdb-ui/pom.xml` and commit the new files under `libs/`.
