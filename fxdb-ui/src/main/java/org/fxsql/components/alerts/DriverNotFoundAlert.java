package org.fxsql.components.alerts;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.exceptions.DriverNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DriverNotFoundAlert extends Alert {
    private static final Logger log = LoggerFactory.getLogger(DriverNotFoundAlert.class);
    private final DriverDownloader driverDownloader;


    public DriverNotFoundAlert(DriverNotFoundException exception, String databaseAdapterType, DriverDownloader driverDownloader) {
        super(AlertType.WARNING);
        this.setTitle("Driver not installed for database: " + databaseAdapterType);
        this.setHeaderText("Driver missing");
        this.driverDownloader = driverDownloader;
        deploy(exception);

        //TODO - On action download default driver
        ButtonType downloadDriverButton = new ButtonType("Download Driver", ButtonBar.ButtonData.OK_DONE);

        // Keep default close button
        this.getButtonTypes().setAll(downloadDriverButton, ButtonType.CLOSE);

        // Handle button action
        this.setResultConverter(button -> {
            System.out.println("Btn clicks---");
            if (button == downloadDriverButton) {
                System.out.println("Download btn clicked");
                assert driverDownloader != null;
                var references = driverDownloader.getReferences();
                var ref = references.stream().filter(r -> r.getDatabaseName().toLowerCase().contains(databaseAdapterType)).findFirst();
                if (ref.isPresent()) {
                    driverDownloader.downloadByReference(ref.get());
                } else {
                    // Set not found !
                    log.warn("driver not listed in the manifest");
                }
            }
            return null;
        });

    }

    private void deploy(Exception exception) {
        var stringWriter = new StringWriter();
        var printWriter = new PrintWriter(stringWriter);
        exception.printStackTrace(printWriter);
        TextArea textArea = new TextArea(stringWriter.toString());
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane content = new GridPane();
        content.setMaxWidth(Double.MAX_VALUE);
        content.add(new Label("Full stacktrace:"), 0, 0);
        content.add(textArea, 0, 1);

        this.getDialogPane().setExpandableContent(content);
    }
}
