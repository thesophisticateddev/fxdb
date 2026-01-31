package org.fxsql.controller;

import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.fxsql.DatabaseConnection;
import org.fxsql.DatabaseConnectionFactory;
import org.fxsql.DatabaseManager;
import org.fxsql.DynamicJDBCDriverLoader;
import org.fxsql.components.alerts.DriverNotFoundAlert;
import org.fxsql.components.alerts.StackTraceAlert;
import org.fxsql.components.common.NumericField;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.driverload.JDBCDriverLoader;
import org.fxsql.driverload.model.DriverReference;
import org.fxsql.events.EventBus;
import org.fxsql.events.NewConnectionAddedEvent;
import org.fxsql.exceptions.DriverNotFoundException;
import org.fxsql.service.BackgroundJarDownloadService;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class NewConnectionController {

    private static final Logger logger = Logger.getLogger(NewConnectionController.class.getName());
    private static final String JAR_DIRECTORY = "dynamic-jars";

    // Observable properties
    private final StringProperty user = new SimpleStringProperty();
    private final StringProperty password = new SimpleStringProperty();
    private final StringProperty hostname = new SimpleStringProperty();
    private final StringProperty connectionString = new SimpleStringProperty();
    private final StringProperty connectionType = new SimpleStringProperty();
    private final StringProperty databaseName = new SimpleStringProperty();
    private final StringProperty connectionAlias = new SimpleStringProperty();
    private final StringProperty databasePort = new SimpleStringProperty();

    @FXML
    public TextField connectionStringTextField;
    @FXML
    public TextField connectionAliasField;
    @FXML
    public ComboBox<String> connectionTypeComboBox;
    @FXML
    public TextField hostnameTextField;
    @FXML
    public PasswordField passwordTextField;
    @FXML
    public TextField userTextField;
    @FXML
    public Button tryConnection;
    @FXML
    public Button connectionButton;
    @FXML
    public Text connectionStatus;
    @FXML
    public TextField databaseNameTextField;
    @FXML
    public ProgressBar downloadProgress;
    @FXML
    public Hyperlink downloadDriverLink;
    @FXML
    public NumericField databasePortField;
    @FXML
    public Label driverStatusLabel;
    @FXML
    public ProgressIndicator connectionProgressIndicator;

    @Inject
    private DriverDownloader driverDownloader;
    @Inject
    private DatabaseManager databaseManager;
    @Inject
    private JDBCDriverLoader driverLoader;

    // Current driver reference for selected database type
    private DriverReference currentDriverReference;

    private String fillTemplate(String template) {
        if (template == null) return "";

        String h = hostname.get() == null ? "" : hostname.get();
        String p = databasePort.get() == null ? "" : databasePort.get();
        String d = databaseName.get() == null ? "" : databaseName.get();
        String u = user.get() == null ? "" : user.get();
        String pw = password.get() == null ? "" : password.get();

        return template
                .replace("{host}", h)
                .replace("{port}", p)
                .replace("{database}", d)
                .replace("{user}", u)
                .replace("{password}", pw)
                .replace("{account}", h); // Snowflake uses {account}
    }

    @FXML
    public void initialize() {
        // Build lookup maps from driver references
        Map<String, DriverReference> referenceMap = driverDownloader.getReferences().stream()
                .collect(Collectors.toMap(
                        ref -> ref.getDatabaseName().trim().toLowerCase(),
                        ref -> ref,
                        (a, b) -> a // Keep first if duplicate
                ));

        Map<String, String> templateMap = driverDownloader.getReferences().stream()
                .collect(Collectors.toMap(
                        ref -> ref.getDatabaseName().trim().toLowerCase(),
                        DriverReference::getUrlTemplate,
                        (a, b) -> a
                ));

        List<String> strReferences = driverDownloader.getReferences().stream()
                .map(r -> r.getDatabaseName().trim().toLowerCase())
                .toList();

        List<Integer> portList = driverDownloader.getReferences().stream()
                .map(r -> r.getDefaultPort() != null ? r.getDefaultPort() : 0)
                .toList();

        var connectionTypes = FXCollections.observableArrayList(strReferences);
        connectionTypeComboBox.setItems(connectionTypes);

        // Set default values
        hostnameTextField.setText("localhost");
        userTextField.setText("user");
        passwordTextField.setText("");
        databaseNameTextField.setText("mydatabase");
        connectionAliasField.setText("connection1");

        // Bidirectional bindings
        user.bindBidirectional(userTextField.textProperty());
        password.bindBidirectional(passwordTextField.textProperty());
        hostname.bindBidirectional(hostnameTextField.textProperty());
        databaseName.bindBidirectional(databaseNameTextField.textProperty());
        connectionAlias.bindBidirectional(connectionAliasField.textProperty());
        connectionType.bind(connectionTypeComboBox.getSelectionModel().selectedItemProperty());
        databasePort.bindBidirectional(databasePortField.textProperty());

        // Connection type change listener - update port and check driver
        connectionType.addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            // Update port based on database type
            if (isFileBasedDatabase(newVal)) {
                databasePort.set("0");
            } else {
                int index = strReferences.indexOf(newVal);
                if (index != -1) {
                    Integer port = portList.get(index);
                    databasePort.set(String.valueOf(port != null ? port : 0));
                }
            }

            // Update current driver reference and check availability
            currentDriverReference = referenceMap.get(newVal.toLowerCase());
            checkDriverAvailability();
        });

        // Connection string binding
        connectionString.bind(Bindings.createStringBinding(() -> {
            String dbType = connectionType.get();
            if (dbType == null) return "";

            String template = templateMap.get(dbType.toLowerCase());

            if (isFileBasedDatabase(dbType)) {
                return template != null ? fillTemplate(template)
                        : String.format("jdbc:%s:./%s.db", dbType, databaseName.get());
            }

            return template != null ? fillTemplate(template) : "No template found";
        }, connectionType, user, password, hostname, databaseName, databasePort));

        connectionStringTextField.textProperty().bind(connectionString);

        // Button handlers
        tryConnection.setOnAction(event -> onTryConnection());

        // Initialize progress indicator as hidden
        if (connectionProgressIndicator != null) {
            connectionProgressIndicator.setVisible(false);
        }

        // Select first item and trigger driver check
        connectionTypeComboBox.getSelectionModel().selectFirst();
    }

    /**
     * Checks if the driver for the currently selected database type is available.
     */
    private void checkDriverAvailability() {
        if (currentDriverReference == null) {
            updateDriverStatus(false, "No driver information available");
            return;
        }

        String jarFileName = currentDriverReference.getJarFileName();
        boolean isAvailable = isDriverJarAvailable(jarFileName);

        // Also check if it's loaded via the driver loader
        String driverClass = currentDriverReference.getDriverClass();
        if (!isAvailable && driverClass != null) {
            isAvailable = driverLoader.isDriverLoaded(driverClass);
        }

        if (isAvailable) {
            updateDriverStatus(true, "Driver available: " + currentDriverReference.getDatabaseName());
        } else {
            updateDriverStatus(false, "Driver not installed");
            setupDriverDownload();
        }
    }

    /**
     * Checks if a driver JAR file exists in the dynamic-jars directory.
     */
    private boolean isDriverJarAvailable(String jarFileName) {
        if (jarFileName == null || jarFileName.isEmpty()) {
            return false;
        }

        // Check exact filename
        File jarFile = new File(JAR_DIRECTORY, jarFileName);
        if (jarFile.exists()) {
            return true;
        }

        // Check for any jar containing the database name
        File jarDir = new File(JAR_DIRECTORY);
        if (jarDir.exists() && jarDir.isDirectory()) {
            String[] files = jarDir.list();
            if (files != null) {
                String dbName = currentDriverReference.getDatabaseName().toLowerCase();
                for (String file : files) {
                    if (file.endsWith(".jar") && file.toLowerCase().contains(dbName.split(" ")[0].toLowerCase())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Updates the driver status display.
     */
    private void updateDriverStatus(boolean available, String message) {
        Platform.runLater(() -> {
            if (driverStatusLabel != null) {
                driverStatusLabel.setText(message);
                driverStatusLabel.setStyle(available ? "-fx-text-fill: green;" : "-fx-text-fill: orange;");
            }

            if (downloadDriverLink != null) {
                downloadDriverLink.setVisible(!available);
                downloadDriverLink.setText(available ? "" : "Download Driver");
            }

            // Enable/disable connection buttons based on driver availability
            tryConnection.setDisable(!available);
            connectionButton.setDisable(!available);
        });
    }

    /**
     * Sets up the driver download link/button.
     */
    private void setupDriverDownload() {
        if (downloadDriverLink == null || currentDriverReference == null) {
            return;
        }

        downloadDriverLink.setOnAction(event -> downloadDriver());
    }

    /**
     * Downloads the driver for the currently selected database type.
     */
    private void downloadDriver() {
        if (currentDriverReference == null) {
            connectionStatus.setText("No driver reference available");
            return;
        }

        String downloadUrl = currentDriverReference.getDownloadLink();
        String jarFileName = currentDriverReference.getJarFileName();

        if (downloadUrl == null || downloadUrl.isEmpty()) {
            connectionStatus.setText("No download URL available for this driver");
            return;
        }

        connectionStatus.setText("Downloading driver...");
        downloadProgress.setVisible(true);
        downloadProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<Boolean> downloadTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    BackgroundJarDownloadService.downloadJarFile(JAR_DIRECTORY, jarFileName, downloadUrl);
                    return true;
                } catch (Exception e) {
                    logger.severe("Failed to download driver: " + e.getMessage());
                    return false;
                }
            }
        };

        downloadTask.setOnSucceeded(event -> {
            boolean success = downloadTask.getValue();
            Platform.runLater(() -> {
                downloadProgress.setProgress(1.0);
                if (success) {
                    connectionStatus.setText("Driver downloaded successfully!");
                    // Reload drivers
                    driverLoader.loadNewDriversAsync(JAR_DIRECTORY,
                            result -> Platform.runLater(() -> {
                                checkDriverAvailability();
                                downloadProgress.setVisible(false);
                            }),
                            null
                    );
                } else {
                    connectionStatus.setText("Failed to download driver");
                    downloadProgress.setVisible(false);
                }
            });
        });

        downloadTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                connectionStatus.setText("Download failed: " + downloadTask.getException().getMessage());
                downloadProgress.setVisible(false);
            });
        });

        new Thread(downloadTask).start();
    }

    private void showDriverNotFoundAlert(String databaseType, DriverNotFoundException exception) {
        DriverNotFoundAlert driverNotFoundAlert = new DriverNotFoundAlert(exception, databaseType, driverDownloader);
        driverNotFoundAlert.show();
    }

    /**
     * Tests the connection without saving it.
     */
    private void onTryConnection() {
        final String adapterType = connectionTypeComboBox.getValue();
        if (adapterType == null) {
            logger.warning("No adapter selected");
            return;
        }

        final String connString = connectionString.get();
        if (connString == null || connString.isEmpty()) {
            connectionStatus.setText("Connection string is empty");
            return;
        }

        // Show progress
        setConnectionInProgress(true);
        connectionStatus.setText("Testing connection...");

        Task<Boolean> testTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                DatabaseConnection connection = DatabaseConnectionFactory.getConnection(adapterType);

                // Set credentials for network-based databases
                if (!isFileBasedDatabase(adapterType)) {
                    connection.setUserName(user.get());
                    connection.setPassword(password.get());
                }

                connection.connect(connString);
                boolean connected = connection.isConnected();

                // Always disconnect after testing
                if (connected) {
                    connection.disconnect();
                }

                return connected;
            }
        };

        testTask.setOnSucceeded(event -> {
            boolean success = testTask.getValue();
            Platform.runLater(() -> {
                setConnectionInProgress(false);
                if (success) {
                    connectionStatus.setText("Connection successful!");
                    connectionStatus.setStyle("-fx-fill: green;");
                } else {
                    connectionStatus.setText("Connection failed");
                    connectionStatus.setStyle("-fx-fill: red;");
                }
            });
        });

        testTask.setOnFailed(event -> {
            Throwable e = testTask.getException();
            Platform.runLater(() -> {
                setConnectionInProgress(false);
                if (e instanceof DriverNotFoundException) {
                    showDriverNotFoundAlert(adapterType, (DriverNotFoundException) e);
                } else {
                    connectionStatus.setText("Error: " + e.getMessage());
                    connectionStatus.setStyle("-fx-fill: red;");
                }
            });
        });

        new Thread(testTask).start();
    }

    /**
     * Shows/hides connection progress indicator.
     */
    private void setConnectionInProgress(boolean inProgress) {
        if (connectionProgressIndicator != null) {
            connectionProgressIndicator.setVisible(inProgress);
        }
        tryConnection.setDisable(inProgress);
        connectionButton.setDisable(inProgress);
    }

    private boolean isFileBasedDatabase(String db) {
        if (db == null) return false;
        String[] fileDbs = new String[]{"sqlite", "duckdb", "h2 database", "apache derby"};
        String lowerDb = db.toLowerCase();
        return Arrays.stream(fileDbs).anyMatch(s -> lowerDb.contains(s.toLowerCase()));
    }

    private void showFailedToConnectAlert(Exception exception) {
        StackTraceAlert alert = new StackTraceAlert(
                Alert.AlertType.ERROR,
                "Error connecting",
                "Failed to connect to database",
                "Expand to see stacktrace",
                exception
        );
        alert.showAndWait();
    }

    @FXML
    public void onCancel() {
        Stage stage = (Stage) connectionButton.getScene().getWindow();
        stage.close();
    }

    @FXML
    public void onConnect() {
        final String adapterType = connectionTypeComboBox.getValue();
        if (adapterType == null) {
            connectionStatus.setText("Please select a database type");
            return;
        }

        final String connString = connectionString.get();
        if (connString == null || connString.isEmpty()) {
            connectionStatus.setText("Connection string is empty");
            return;
        }

        final String alias = connectionAlias.get();
        if (alias == null || alias.isEmpty()) {
            connectionStatus.setText("Please enter a connection name");
            return;
        }

        setConnectionInProgress(true);
        connectionStatus.setText("Connecting...");

        Task<DatabaseConnection> connectTask = new Task<>() {
            @Override
            protected DatabaseConnection call() throws Exception {
                DatabaseConnection connection = DatabaseConnectionFactory.getConnection(adapterType);

                // Set credentials for network-based databases
                if (!isFileBasedDatabase(adapterType)) {
                    connection.setUserName(user.get());
                    connection.setPassword(password.get());
                }

                connection.connect(connString);
                return connection;
            }
        };

        connectTask.setOnSucceeded(event -> {
            DatabaseConnection connection = connectTask.getValue();
            Platform.runLater(() -> {
                setConnectionInProgress(false);

                if (connection != null && connection.isConnected()) {
                    // Save the connection
                    saveConnection(adapterType, connection);

                    // Fire event and close window
                    EventBus.fireEvent(new NewConnectionAddedEvent("Connection Added: " + alias));

                    Stage stage = (Stage) connectionButton.getScene().getWindow();
                    stage.close();
                } else {
                    connectionStatus.setText("Failed to establish connection");
                    connectionStatus.setStyle("-fx-fill: red;");
                }
            });
        });

        connectTask.setOnFailed(event -> {
            Throwable e = connectTask.getException();
            Platform.runLater(() -> {
                setConnectionInProgress(false);
                if (e instanceof DriverNotFoundException) {
                    showDriverNotFoundAlert(adapterType, (DriverNotFoundException) e);
                } else {
                    showFailedToConnectAlert((Exception) e);
                }
            });
        });

        new Thread(connectTask).start();
    }

    /**
     * Saves the connection to the DatabaseManager with all required metadata.
     */
    private void saveConnection(String adapterType, DatabaseConnection connection) {
        String alias = connectionAlias.get();

        if (isFileBasedDatabase(adapterType)) {
            // File-based database (SQLite, DuckDB, etc.)
            String filePath = connectionString.get();
            databaseManager.addConnection(alias, filePath, adapterType, connection);
        } else {
            // Network-based database (PostgreSQL, MySQL, etc.)
            databaseManager.addConnection(
                    alias,
                    adapterType,                    // dbVendor
                    hostname.get(),                 // host
                    databasePort.get(),             // port
                    user.get(),                     // user
                    password.get(),                 // password
                    connection
            );

            // Also update the URL in metadata
            var metaData = databaseManager.getConnectionMetaData(alias);
            if (metaData != null) {
                metaData.setUrl(connectionString.get());
                metaData.setDatabase(databaseName.get());
            }
        }

        logger.info("Connection saved: " + alias + " (" + adapterType + ")");
    }
}
