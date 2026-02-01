package org.fxsql.components.notifications;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A toast notification that floats above the UI and auto-dismisses.
 */
public class ToastNotification extends Notification {

    public enum Type {
        SUCCESS,
        ERROR,
        WARNING,
        INFO
    }

    private static final Duration DEFAULT_DURATION = Duration.seconds(4);
    private static final Duration FADE_DURATION = Duration.millis(300);

    private final Type type;
    private final Duration displayDuration;

    public ToastNotification(String message, Type type) {
        this(message, type, DEFAULT_DURATION);
    }

    public ToastNotification(String message, Type type, Duration duration) {
        super(message, new FontIcon(getIconForType(type)));
        this.type = type;
        this.displayDuration = duration;

        // Apply styles based on type
        this.getStyleClass().addAll(Styles.ELEVATED_4);
        applyTypeStyle();

        // Set preferred width
        this.setMaxWidth(350);
        this.setMinWidth(250);
    }

    private static Ikon getIconForType(Type type) {
        return switch (type) {
            case SUCCESS -> Feather.CHECK_CIRCLE;
            case ERROR -> Feather.X_CIRCLE;
            case WARNING -> Feather.ALERT_TRIANGLE;
            case INFO -> Feather.INFO;
        };
    }

    private void applyTypeStyle() {
        switch (type) {
            case SUCCESS -> this.getStyleClass().add(Styles.SUCCESS);
            case ERROR -> this.getStyleClass().add(Styles.DANGER);
            case WARNING -> this.getStyleClass().add(Styles.WARNING);
            case INFO -> this.getStyleClass().add(Styles.ACCENT);
        }
    }

    /**
     * Shows the notification in the given container with auto-dismiss.
     */
    public void show(StackPane container) {
        if (container == null) {
            return;
        }

        Platform.runLater(() -> {
            // Position at top-right
            StackPane.setAlignment(this, Pos.TOP_RIGHT);
            StackPane.setMargin(this, new Insets(10, 10, 0, 0));

            // Make sure it's above other content
            this.setViewOrder(-1);

            // Set close action
            this.setOnClose(event -> {
                dismissWithAnimation(container);
            });

            // Add to container with fade-in animation
            container.getChildren().add(this);
            Animations.fadeIn(this, FADE_DURATION).playFromStart();

            // Auto-dismiss after duration
            PauseTransition pause = new PauseTransition(displayDuration);
            pause.setOnFinished(e -> dismissWithAnimation(container));
            pause.play();
        });
    }

    /**
     * Shows the notification in a VBox container (for stacking multiple notifications).
     */
    public void showInStack(VBox notificationStack) {
        if (notificationStack == null) {
            return;
        }

        Platform.runLater(() -> {
            // Set close action
            this.setOnClose(event -> {
                dismissFromStack(notificationStack);
            });

            // Add to stack with fade-in animation
            notificationStack.getChildren().add(0, this);
            Animations.fadeIn(this, FADE_DURATION).playFromStart();

            // Auto-dismiss after duration
            PauseTransition pause = new PauseTransition(displayDuration);
            pause.setOnFinished(e -> dismissFromStack(notificationStack));
            pause.play();
        });
    }

    private void dismissWithAnimation(StackPane container) {
        var fadeOut = Animations.fadeOut(this, FADE_DURATION);
        fadeOut.setOnFinished(e -> {
            container.getChildren().remove(this);
        });
        fadeOut.playFromStart();
    }

    private void dismissFromStack(VBox stack) {
        var fadeOut = Animations.fadeOut(this, FADE_DURATION);
        fadeOut.setOnFinished(e -> {
            stack.getChildren().remove(this);
        });
        fadeOut.playFromStart();
    }

    // Static factory methods for convenience
    public static ToastNotification success(String message) {
        return new ToastNotification(message, Type.SUCCESS);
    }

    public static ToastNotification error(String message) {
        return new ToastNotification(message, Type.ERROR);
    }

    public static ToastNotification warning(String message) {
        return new ToastNotification(message, Type.WARNING);
    }

    public static ToastNotification info(String message) {
        return new ToastNotification(message, Type.INFO);
    }
}