package org.fxsql.service;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.IOException;

@Singleton
public class WindowManager {

    private final Injector injector;

    @Inject
    public WindowManager(Injector injector) {
        this.injector = injector;
    }

    /**
     * Loads FXML and returns the root node with injected controller
     */
    public <T> WindowResult<T> loadWindow(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getClassLoader().getResource(fxmlPath)
        );
        loader.setControllerFactory(injector::getInstance);

        Parent root = loader.load();
        T controller = loader.getController();

        return new WindowResult<>(root, controller);
    }

    /**
     * Opens a new window (non-modal)
     */
    public <T> Stage openWindow(String fxmlPath, String title) throws IOException {
        return openWindow(fxmlPath, title, null, false);
    }

    /**
     * Opens a modal dialog
     */
    public <T> Stage openDialog(String fxmlPath, String title, Stage owner) throws IOException {
        return openWindow(fxmlPath, title, owner, true);
    }

    /**
     * Generic window opening method
     */
    public <T> Stage openWindow(String fxmlPath, String title, Stage owner, boolean modal) throws IOException {
        WindowResult<T> result = loadWindow(fxmlPath);

        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(new Scene(result.root));

        if (modal && owner != null) {
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(owner);
        }

        stage.show();
        return stage;
    }

    /**
     * Result class containing both root node and controller
     */
    public static class WindowResult<T> {
        public final Parent root;
        public final T controller;

        public WindowResult(Parent root, T controller) {
            this.root = root;
            this.controller = controller;
        }
    }
}