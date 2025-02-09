# FXDB

FXDB is a JavaFX-based application designed to manage and interact with various database connections dynamically, similar to tools like DBeaver. It provides a user-friendly interface to add, edit, delete, and view database connections, ensuring encrypted storage of sensitive information.

### Features
 1. Dynamic Connection Management: Add, edit, and delete database connections on-the-fly.
 2. Secure Storage: Encrypts database passwords before saving to ensure security.
 3. Multiple Database Support: Supports various databases including SQLite, MySQL, PostgreSQL, and more.

## Installation
Clone the Repository:

```bash
git clone https://github.com/thesophisticateddev/fxdb.git
cd fxdb
```

Build the Application: Ensure you have Java 11 or higher and Gradle installed. Then, run:

```bash
mvn clean build
```


# Usage
Upon launching the application:

1. Add a Connection: Navigate to the "Manage Connections" menu to add a new database connection. Enter the required details, and the password will be encrypted before saving.
2. View Connections: Existing connections are listed on the left panel. Select a connection to view its databases and tables.
3. Execute Queries: Use the main panel to execute SQL queries and view results.


# Contributing
Contributions are welcome! Please fork the repository and submit a pull request with your changes. Ensure that your code adheres to the project's coding standards and includes appropriate tests.

# License
This project is licensed under the MIT License. See the LICENSE file for details.