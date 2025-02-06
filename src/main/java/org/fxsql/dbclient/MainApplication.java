package org.fxsql.dbclient;

import atlantafx.base.theme.PrimerLight;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxsql.dbclient.db.DatabaseManager;
import org.fxsql.dbclient.db.DatabaseModule;

import java.io.IOException;

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
        FXMLLoader fxmlLoader =
                new FXMLLoader(MainApplication.class.getResource("main.fxml"),
                        null, null, injector::getInstance);

        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        databaseManager.closeAll();
    }
}