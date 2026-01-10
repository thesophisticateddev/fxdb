package org.fxsql.controller;

import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
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
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.driverload.JDBCDriverLoader;
import org.fxsql.events.EventBus;
import org.fxsql.events.NewConnectionAddedEvent;
import org.fxsql.exceptions.DriverNotFoundException;

import java.util.Arrays;
import java.util.List;
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
    private DriverDownloader driverDownloader;
    @Inject
    private DatabaseManager databaseManager;
    @Inject
    private JDBCDriverLoader driverLoader;

    //    public void setDatabaseManager(DatabaseManager databaseManager) {
//        this.databaseManager = databaseManager;
//    }
//
//    public void setDriverDownloader(DriverDownloader d){
//        this.driverDownloader = d;
//    }
    @FXML
    public void initialize() {
        // Initialize ComboBox with connection types
        List<String> strReferences = driverDownloader.getReferences().stream().map(r -> r.getDatabaseName().trim().toLowerCase()).toList();
        var connectionTypes = FXCollections.observableArrayList(strReferences);
        connectionTypeComboBox.setItems(connectionTypes);
        connectionTypeComboBox.getSelectionModel().selectFirst();

        // Set default values
        hostnameTextField.setText("localhost");
        userTextField.setText("user");
        passwordTextField.setText("test");
        databaseNameTextField.setText("mydatabase");
        connectionAliasField.setText("connection1");

        // Use bindBidirectional() to allow UI changes to reflect in properties
        user.bindBidirectional(userTextField.textProperty());
        password.bindBidirectional(passwordTextField.textProperty());
        hostname.bindBidirectional(hostnameTextField.textProperty());
        databaseName.bindBidirectional(databaseNameTextField.textProperty());
        connectionAlias.bindBidirectional(connectionAliasField.textProperty());
        connectionType.bind(connectionTypeComboBox.getSelectionModel().selectedItemProperty());

        // Fix binding for connectionString
        connectionString.bind(Bindings.createStringBinding(() -> {
            String dbType = connectionType.get();
            if (isFileBasedDatabase(dbType)) {
                return String.format("jdbc:%s:./%s.db", connectionType.get(), databaseName.get());
            }
            return String.format("jdbc:%s://%s:%s@%s/%s", dbType, user.get(), password.get(), hostname.get(), databaseName.get());
        }, connectionType, user, password, hostname, databaseName)); // Add all dependencies

        // Bind connectionString to the TextField so it updates in the UI
        connectionStringTextField.textProperty().bindBidirectional(connectionString);

        // Set up event handler for the tryConnectionButton
        tryConnection.setOnAction(event -> onTryConnection());

        checkInstalledDrivers();
    }


    private void checkInstalledDrivers() {
        //Check for Sqlite drivers
        downloadDriverLink.setMnemonicParsing(true);
        if (!DynamicJDBCDriverLoader.isSqliteJDBCJarAvailable()) {
            // Set connection status
            connectionStatus.setText("Sqlite driver not installed");
            downloadDriverLink.setVisible(true);

            downloadDriverLink.setOnAction(event -> {
                ReadOnlyDoubleProperty rdbp = dynamicJDBCDriverLoader.downloadDriverInTheBackground("sqlite");
                downloadProgress.progressProperty().bind(rdbp);
            });
        } else {
            downloadDriverLink.setVisible(false);
        }
    }

    private void showDriverNotFoundAlert(String databaseType, DriverNotFoundException exception) {
        DriverNotFoundAlert driverNotFoundAlert = new DriverNotFoundAlert(exception, databaseType, driverDownloader);
        driverNotFoundAlert.show();
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

        try {
            //Try connecting to the database
            connection.connect(connectionString);
        } catch (DriverNotFoundException e) {
            showDriverNotFoundAlert(adapterType, e);
        } catch (Exception e) {
            showFailedToConnectAlert(e);
            return;
        }
        if (connection.isConnected()) {
            connectionStatus.setText("Connection Successful!");
            if (databaseManager != null) {
                databaseManager.addConnection(connectionAlias.getValue(), connectionString, connectionType.get(), connection);
            }
        } else {
            connectionStatus.setText("Not connected!");
        }
        //Write status to UI
        connection.disconnect();
    }

    private boolean isFileBasedDatabase(String db) {
        String[] fileDbs = new String[]{"sqlite", "indexDb"};
        return Arrays.stream(fileDbs).anyMatch(s -> s.equalsIgnoreCase(db));
    }

    private void showFailedToConnectAlert(Exception exception) {
        StackTraceAlert alert = new StackTraceAlert(Alert.AlertType.ERROR, "Error connecting", "Failed to connect to database", "Expand to see stacktrace", exception);
        alert.showAndWait();
    }

    @FXML
    public void onConnect() {
        // connect to the database
        // save connection to the manager
        final String adapterType = connectionTypeComboBox.getValue();

        DatabaseConnection connection = DatabaseConnectionFactory.getConnection(adapterType);
        final String connectionString = connectionStringTextField.getText();

        if (connectionString == null || connectionString.isEmpty()) {
            return;
        }
        try {
            //Try connecting to the database
            connection.connect(connectionString);
        } catch (Exception e) {
            showFailedToConnectAlert(e);
            return;
        }
        if (isFileBasedDatabase(adapterType)) {
            assert databaseManager != null;
            databaseManager.addConnection(connectionAlias.getValue(), connectionString, connectionType.get(), connection);
        } else {
            //Connection based database
            assert databaseManager != null;
            databaseManager.addConnection(connectionAlias.getValue(), connectionType.get(), hostname.get(), "", user.get(), password.get(), connection);
        }

        //Dispatch Event for updating the combo box on the Main UIh
        EventBus.fireEvent(new NewConnectionAddedEvent("Connection Added"));

        Platform.runLater(() -> {
            Stage stage = (Stage) connectionButton.getScene().getWindow();
            System.out.println("Closing window!");
            stage.close();
        });
    }
}
