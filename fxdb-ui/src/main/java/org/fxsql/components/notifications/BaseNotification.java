package org.fxsql.components.notifications;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public abstract class BaseNotification extends Notification {
    public BaseNotification(String message, Ikon icon) {
        super(message, new FontIcon(icon));
        this.getStyleClass().addAll(Styles.ELEVATED_1);
        StackPane.setAlignment(this, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(this, new Insets(10,10,0,0));
    }

    public abstract void show(StackPane panel);
}
