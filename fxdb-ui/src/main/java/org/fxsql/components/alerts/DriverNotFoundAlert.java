package org.fxsql.components.alerts;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.fxsql.exceptions.DriverNotFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DriverNotFoundAlert extends Alert {
    public DriverNotFoundAlert(DriverNotFoundException exception, String databaseAdapterType) {
        super(AlertType.WARNING);
        this.setTitle("Driver not installed for database: " + databaseAdapterType);
        this.setHeaderText("Driver missing");
        deploy(exception);

        //TODO - On action download default driver
        ButtonType downloadDriverButton = new ButtonType("Download Driver", ButtonBar.ButtonData.OK_DONE);

        // Keep default close button
        this.getButtonTypes().setAll(downloadDriverButton, ButtonType.CLOSE);

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
