package org.fxsql.dbclient.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import org.fxsql.dbclient.db.DatabaseConnection;
import org.fxsql.dbclient.db.DatabaseConnectionFactory;
import org.fxsql.dbclient.db.DynamicJDBCDriverLoader;

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

    @FXML
    public TextField connectionStringTextField;
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

    private final DynamicJDBCDriverLoader dynamicJDBCDriverLoader = new DynamicJDBCDriverLoader();
    public Hyperlink downloadDriverLink;

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

        // Bind properties to TextFields
        user.bind(userTextField.textProperty());
        password.bind(passwordTextField.textProperty());
        hostname.bind(hostnameTextField.textProperty());
        connectionType.bind(connectionTypeComboBox.valueProperty());
        databaseName.bind(databaseNameTextField.textProperty());

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


    private void checkInstalledDrivers(){
        //Check for Sqlite drivers
        downloadDriverLink.setMnemonicParsing(true);
        if(!dynamicJDBCDriverLoader.isSqliteJDBCJarAvailable()){
            // Set connection status
            connectionStatus.setText("Sqlite driver not installed");
            downloadDriverLink.setVisible(true);

            downloadDriverLink.setOnAction(event -> dynamicJDBCDriverLoader.downloadDriverInTheBackground("sqlite",downloadProgress));
        }
        else{
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
        connection.setDownloadProgressBar(downloadProgress);

        //Try connecting to the database
        connection.connect(connectionString);
        if (connection.isConnected()) {
            connectionStatus.setText("Connection Successful!");
        }
        else {
            connectionStatus.setText("Not connected!");
        }
        //Write status to UI
        connection.disconnect();
    }
}
