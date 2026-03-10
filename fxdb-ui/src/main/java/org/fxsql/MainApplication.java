package org.fxsql;

import atlantafx.base.theme.PrimerLight;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.dockfx.DockPane;

import java.io.IOException;
import java.util.Objects;

public class MainApplication extends Application {

    protected Injector injector;
    private MainController mainController;
    private Stage splashStage;

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void init() {
        // Guice setup runs on the launcher thread (not FX thread) — no UI lag
        injector = Guice.createInjector(new DatabaseModule());
    }

    @Override
    public void start(Stage primaryStage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // Show splash immediately
        showSplash();

        // Load main UI in the background
        Task<Parent> loadTask = new Task<>() {
            @Override
            protected Parent call() throws IOException {
                FXMLLoader fxmlLoader = new FXMLLoader(
                        getClass().getClassLoader().getResource("main.fxml"),
                        null, null, injector::getInstance
                );
                Parent root = fxmlLoader.load();
                mainController = fxmlLoader.getController();
                return root;
            }
        };

        loadTask.setOnSucceeded(event -> {
            Parent root = loadTask.getValue();
            Scene scene = new Scene(root, 1200, 800);

            scene.getStylesheets().add(DockPane.getDefaultUserAgentStyleheet());
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getClassLoader().getResource("stylesheets/table-view.css")).toExternalForm()
            );
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getClassLoader().getResource("stylesheets/dock-theme.css")).toExternalForm()
            );

            setApplicationIcon(primaryStage);
            primaryStage.setTitle("FXDB");
            primaryStage.setScene(scene);
            primaryStage.show();

            splashStage.close();
        });

        loadTask.setOnFailed(event -> {
            splashStage.close();
            Throwable ex = loadTask.getException();
            ex.printStackTrace();
            Platform.exit();
        });

        Thread loadThread = new Thread(loadTask, "UI-Loader");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void showSplash() {
        Image splashImage = new Image(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("images/splashscreen.png"))
        );

        ImageView imageView = new ImageView(splashImage);
        imageView.setFitWidth(600);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        StackPane splashRoot = new StackPane(imageView);
        splashRoot.setStyle("-fx-background-color: #000000;");

        Scene splashScene = new Scene(splashRoot);

        splashStage = new Stage(StageStyle.UNDECORATED);
        splashStage.setScene(splashScene);
        splashStage.centerOnScreen();
        splashStage.show();
    }

    private void setApplicationIcon(Stage stage) {
        Image icon = new Image(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("icons/icon.png")));
        stage.getIcons().add(icon);
    }

    @Override
    public void stop() {
        if (mainController != null) {
            mainController.shutdown();
        }
        System.exit(0);
    }
}
