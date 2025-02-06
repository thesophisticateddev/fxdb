package org.fxsql.dbclient.components;

import atlantafx.base.theme.Styles;
import javafx.scene.control.Button;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class CircularButton extends Button {
    public CircularButton(){
        this.getStyleClass().addAll(Styles.BUTTON_CIRCLE);
    }
    public void setIcon(Feather icon){
        this.setGraphic(new FontIcon(icon));
    }
}
