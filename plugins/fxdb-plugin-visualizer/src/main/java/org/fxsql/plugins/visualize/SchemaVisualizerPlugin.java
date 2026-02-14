package org.fxsql.plugins.visualize;

import javafx.application.Platform;
import org.fxsql.plugins.AbstractPlugin;
import org.fxsql.plugins.FXPlugin;

import java.util.logging.Level;

@FXPlugin(id = "schema-visualizer")
public class SchemaVisualizerPlugin extends AbstractPlugin {

    private SchemaVisualizerStage stage;

    @Override
    public String getId() {
        return "schema-visualizer";
    }

    @Override
    public String getName() {
        return "Schema Visualizer";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    protected void onInitialize() {
        logger.info("Schema Visualizer plugin initialized");
    }

    @Override
    protected void onStart() {
        logger.info("Schema Visualizer plugin starting");
        Platform.runLater(() -> {
            try {
                stage = new SchemaVisualizerStage();
                stage.show();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to open Schema Visualizer", e);
            }
        });
    }

    @Override
    protected void onStop() {
        logger.info("Schema Visualizer plugin stopping");
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
                stage = null;
            }
        });
    }
}
