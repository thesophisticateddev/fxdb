package org.fxsql.fxdb.listeners;

import javafx.scene.layout.StackPane;

public class BaseListener {
    protected StackPane notificationPanel;

    public void setNotificationPanel(StackPane panel){
        this.notificationPanel = panel;
    }
}
