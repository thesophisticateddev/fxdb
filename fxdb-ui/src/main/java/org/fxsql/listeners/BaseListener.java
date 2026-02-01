package org.fxsql.listeners;

import org.fxsql.components.notifications.NotificationContainer;

public class BaseListener {
    protected NotificationContainer notificationContainer;

    public void setNotificationContainer(NotificationContainer container) {
        this.notificationContainer = container;
    }
}