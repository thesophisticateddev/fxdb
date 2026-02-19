package org.fxsql.plugins.nosql;

import javafx.application.Platform;
import org.fxsql.plugins.AbstractPlugin;
import org.fxsql.plugins.FXPlugin;

import java.util.logging.Level;

@FXPlugin(id = "mongodb-connector")
public class MongoDBPlugin extends AbstractPlugin {

    private MongoDBStage stage;

    @Override
    public String getId() {
        return "mongodb-connector";
    }

    @Override
    public String getName() {
        return "MongoDB Connector";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    protected void onInitialize() {
        logger.info("MongoDB Connector plugin initialized");
    }

    @Override
    protected void onStart() {
        logger.info("MongoDB Connector plugin starting");
        Platform.runLater(() -> {
            try {
                stage = new MongoDBStage();
                stage.show();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to open MongoDB Connector", e);
            }
        });
    }

    @Override
    protected void onStop() {
        logger.info("MongoDB Connector plugin stopping");
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
                stage = null;
            }
        });
    }
}
