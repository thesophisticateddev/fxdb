package org.fxsql.controller;

import com.google.inject.Inject;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import org.fxsql.DatabaseConnection;
import org.fxsql.DatabaseConnectionFactory;
import org.fxsql.DatabaseManager;
import org.fxsql.DynamicJDBCDriverLoader;

import java.util.Arrays;
import java.util.logging.Logger;

public class NewConnectionController {

    private static final Logger logger = Logger.getLogger(NewConnectionController.class.getName());

    // Observable properties
    private final StringProperty user = new SimpleStringProperty();
    private final StringProperty password = new SimpleStringProperty();
    private final StringProperty hostname = new SimpleStringProperty();
    private final StringProperty connectionString = new SimpleStringProperty();
    private final StringProperty connectionType = new SimpleStringProperty();
    private final StringProperty databaseName = new SimpleStringProperty();

    private final StringProperty connectionAlias = new SimpleStringProperty();
    private final DynamicJDBCDriverLoader dynamicJDBCDriverLoader = new DynamicJDBCDriverLoader();
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
    public ProgressBar downloadProgress;
    public Hyperlink downloadDriverLink;

    @Inject
    private DatabaseManager databaseManager;

    @FXML
    public void initialize() {
        // Initialize ComboBox with connection types
        var connectionTypes = FXCollections.observableArrayList("sqlite", "postgres", "mysql");
        connectionTypeComboBox.setItems(connectionTypes);
        connectionTypeComboBox.getSelectionModel().selectFirst();

        // Set default values
        hostnameTextField.setText("localhost");
        userTextField.setText("user");
        passwordTextField.setText("test");
        databaseNameTextField.setText("mydatabase");
        connectionAliasField.setText("connection1");
        // Bind properties to TextFields
        user.bind(userTextField.textProperty());
        password.bind(passwordTextField.textProperty());
        hostname.bind(hostnameTextField.textProperty());
        connectionType.bind(connectionTypeComboBox.valueProperty());
        databaseName.bind(databaseNameTextField.textProperty());
        connectionAlias.bind(connectionAliasField.textProperty());

        if (connectionType.get().contains("sqlite")) {
            connectionStringTextField.setText("jdbc:sqlite:./sample.db");
        }
        // Bind connectionString to the concatenation of user, password, and hostname
        connectionString.bind(Bindings.createStringBinding(() -> {
            if (connectionType.get().contains("sqlite")) {
                return String.format("jdbc:sqlite:./%s.db", databaseName.get());
            }
            return String.format("jdbc:%s://%s:%s@%s/%s", connectionType.get(), user.get(), password.get(),
                    hostname.get(), databaseName.get());
        }));

        // Bind connectionString to the TextField
        connectionStringTextField.textProperty().bind(connectionString);

        // Set up event handler for the tryConnectionButton
        tryConnection.setOnAction(event -> onTryConnection());

        checkInstalledDrivers();
    }


    private void checkInstalledDrivers() {
        //Check for Sqlite drivers
        downloadDriverLink.setMnemonicParsing(true);
        if (!dynamicJDBCDriverLoader.isSqliteJDBCJarAvailable()) {
            // Set connection status
            connectionStatus.setText("Sqlite driver not installed");
            downloadDriverLink.setVisible(true);

            downloadDriverLink.setOnAction(event -> {
                ReadOnlyDoubleProperty rdbp = dynamicJDBCDriverLoader.downloadDriverInTheBackground("sqlite");
                downloadProgress.progressProperty().bind(rdbp);
            });
        }
        else {
            downloadDriverLink.setVisible(false);
        }
    }

    private void onTryConnection() {
        final String adapterType = connectionTypeComboBox.getValue();
        if (adapterType == null) {
            logger.warning("No adapter selected");
            return;
        }
        DatabaseConnection connection = DatabaseConnectionFactory.getConnection(adapterType);
        final String connectionString = connectionStringTextField.getText();
        if (connectionString == null || connectionString.isEmpty()) {
            return;
        }

        //Set the progress bar
//        connection.setDownloadProgressBar(downloadProgress);

        //Try connecting to the database
        connection.connect(connectionString);
        if (connection.isConnected()) {
            connectionStatus.setText("Connection Successful!");
            if (databaseManager != null) {
                databaseManager.addConnection(connectionAlias.getValue(), connectionType.get(), connectionString,
                        connection);
            }
        }
        else {
            connectionStatus.setText("Not connected!");
        }
        //Write status to UI
        connection.disconnect();
    }

    private boolean isFileBasedDatabase(String db) {
        String[] fileDbs = new String[]{"sqlite", "indexDb"};
        return Arrays.stream(fileDbs).anyMatch(s -> s.equalsIgnoreCase(db));
    }

    private void onConnect() {
        // connect to the database
        // save connection to the manager
        final String adapterType = connectionTypeComboBox.getValue();

        DatabaseConnection connection = DatabaseConnectionFactory.getConnection(adapterType);
        final String connectionString = connectionStringTextField.getText();

        if (connectionString == null || connectionString.isEmpty()) {
            return;
        }
        //Try connecting to the database
        connection.connect(connectionString);

        if (isFileBasedDatabase(adapterType)) {
            databaseManager.addConnection(connectionAlias.getValue(), connectionType.get(),connectionString, connection);
        }
        else {
            //Connection based database
            databaseManager.addConnection(connectionAlias.getValue(), connectionType.get(), hostname.get(), "",
                    user.get(), password.get(), connection);
        }
    }
}
