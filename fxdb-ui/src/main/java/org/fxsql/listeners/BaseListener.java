package org.fxsql.listeners;

import javafx.scene.layout.VBox;

public class BaseListener {
    protected VBox notificationPanel;

    public void setNotificationPanel(VBox panel){
        this.notificationPanel = panel;
    }
}
