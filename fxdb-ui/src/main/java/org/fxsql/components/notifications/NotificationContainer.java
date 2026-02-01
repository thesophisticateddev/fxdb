package org.fxsql.components.notifications;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A container for floating toast notifications.
 * Notifications stack vertically and auto-dismiss.
 */
public class NotificationContainer extends StackPane {

    private final VBox notificationStack;

    public NotificationContainer() {
        this.notificationStack = new VBox(8);
        notificationStack.setAlignment(Pos.TOP_RIGHT);
        notificationStack.setPadding(new Insets(10));
        notificationStack.setPickOnBounds(false);
        notificationStack.setMouseTransparent(false);

        // Position the stack at top-right
        StackPane.setAlignment(notificationStack, Pos.TOP_RIGHT);

        // Make the container transparent to mouse events except for notifications
        this.setPickOnBounds(false);
        this.setMouseTransparent(false);

        this.getChildren().add(notificationStack);
    }

    /**
     * Shows a toast notification.
     */
    public void showNotification(ToastNotification notification) {
        notification.showInStack(notificationStack);
    }

    /**
     * Shows a success notification.
     */
    public void showSuccess(String message) {
        ToastNotification.success(message).showInStack(notificationStack);
    }

    /**
     * Shows an error notification.
     */
    public void showError(String message) {
        ToastNotification.error(message).showInStack(notificationStack);
    }

    /**
     * Shows a warning notification.
     */
    public void showWarning(String message) {
        ToastNotification.warning(message).showInStack(notificationStack);
    }

    /**
     * Shows an info notification.
     */
    public void showInfo(String message) {
        ToastNotification.info(message).showInStack(notificationStack);
    }

    /**
     * Clears all notifications.
     */
    public void clearAll() {
        notificationStack.getChildren().clear();
    }
}