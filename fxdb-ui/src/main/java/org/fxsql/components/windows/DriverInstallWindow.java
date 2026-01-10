package org.fxsql.components.windows;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fxsql.controller.DriverInstallController;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class DriverInstallWindow {

    private DriverInstallWindow() {}

    public static void open(Window owner,
                            List<DriverInstallController.DriverCandidate> candidates,
                            Consumer<DriverInstallController.DriverCandidate> onInstall) {

        try {
            FXMLLoader loader = new FXMLLoader(
                    DriverInstallWindow.class.getResource("install-driver.fxml")
            );

            Parent root = loader.load();
            DriverInstallController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Driver Installer");
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setScene(new Scene(root, 560, 320));

            controller.init(stage, candidates, onInstall);

            stage.showAndWait();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DriverInstallView.fxml", e);
        }
    }
}
