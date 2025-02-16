package org.fxsql.components.alerts;

import javafx.scene.control.Alert;

public class ConnectionFailedAlert extends Alert {
    public ConnectionFailedAlert(Exception e){
        super(AlertType.ERROR);
        this.setHeaderText("Error occured while connecting to database!");
        this.setTitle("Error Connecting");

    }
}
