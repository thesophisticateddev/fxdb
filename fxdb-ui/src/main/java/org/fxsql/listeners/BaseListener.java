package org.fxsql.listeners;

import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class BaseListener {
    protected StackPane notificationPanel;

    public void setNotificationPanel(StackPane panel){
        this.notificationPanel = panel;
    }
}
