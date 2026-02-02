# FXDB

FXDB is a JavaFX-based application designed to manage and interact with various database connections dynamically, similar to tools like DBeaver. It provides a user-friendly interface to add, edit, delete, and view database connections, ensuring encrypted storage of sensitive information.

### Features
1. Dynamic Connection Management: Add, edit, and delete database connections on-the-fly.
2. Secure Storage: Encrypts database passwords before saving to ensure security.
3. Multiple Database Support: Supports various databases including SQLite, MySQL, PostgreSQL, and more.

## Requirements

- Java 17 or higher
- Maven 3.8+

## Installation

Clone the Repository:

```bash
git clone https://github.com/thesophisticateddev/fxdb.git
cd fxdb
```

Build the Application:

```bash
mvn clean install
```

## Running the Application

### Option 1: Using Maven JavaFX Plugin (Recommended for Development)

```bash
cd fxdb-ui
mvn javafx:run
```

### Option 2: Using the Shaded JAR

After building, run the shaded JAR directly:

```bash
java -jar fxdb-ui/target/fxdb-ui-1.0.0-shaded.jar
```

### Option 3: Running from IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Go to **Run → Edit Configurations**
3. Create a new **Application** configuration:
   - **Main class**: `org.fxsql.Launcher`
   - **Use classpath of module**: `fxdb-ui`
   - **Working directory**: Project root

No VM options are required when using the Launcher class.

## Packaging

### Create a Fat JAR (Shaded JAR)

```bash
cd fxdb-ui
mvn clean package
```

The shaded JAR will be at: `fxdb-ui/target/fxdb-ui-1.0.0-shaded.jar`

### Create Native Installer (requires JDK with jpackage)

```bash
cd fxdb-ui
mvn clean package jpackage:jpackage
```

Native installers will be created in: `fxdb-ui/target/dist/`

## Usage

Upon launching the application:

1. **Add a Connection**: Navigate to the "File" menu and select "New Connection" to add a new database connection. Enter the required details, and the password will be encrypted before saving.
2. **Edit/Remove Connections**: Right-click on the connection dropdown to edit or remove existing connections.
3. **View Connections**: Existing connections are listed in the left panel dropdown. Select a connection to view its databases and tables.
4. **Execute Queries**: Use the main panel to execute SQL queries and view results.

## Project Structure

```
fxdb/
├── fxdb-core/      # Core utilities (encryption, window management)
├── fxdb-db/        # Database connection layer
├── fxdb-ui/        # JavaFX UI application
└── META-DATA/      # Connection storage (gitignored)
```

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request with your changes. Ensure that your code adheres to the project's coding standards and includes appropriate tests.

## License

This project is licensed under the MIT License. See the LICENSE file for details.