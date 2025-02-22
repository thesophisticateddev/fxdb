package org.fxsql.fxdb;

import atlantafx.base.theme.PrimerLight;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.fxsql.fxdb.databaseManagement.DatabaseManager;
import org.fxsql.fxdb.databaseManagement.DatabaseModule;

import java.io.IOException;
import java.util.Objects;

public class MainApplication extends Application {

    protected Injector injector;
    private DatabaseManager databaseManager;

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void init() {
        databaseManager = new DatabaseManager();
        injector = Guice.createInjector(new DatabaseModule());
    }

    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        System.out.println("Resource URL: " + getClass().getResource("main.fxml"));
        System.out.println("ClassLoader URL: " + getClass().getClassLoader().getResource("main.fxml"));

        FXMLLoader fxmlLoader =
                new FXMLLoader(getClass().getResource("main.fxml"), null, null, injector::getInstance);
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        setApplicationIcon(stage);
        stage.setTitle("fxdb");
        stage.setScene(scene);
        stage.show();
    }

    private void setApplicationIcon(Stage stage) {
        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("icons/icon.png")));
        stage.getIcons().add(icon);
    }

    @Override
    public void stop() {
        databaseManager.closeAll();
//        System.exit(1);
    }
}