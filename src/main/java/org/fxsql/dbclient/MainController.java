package org.fxsql.dbclient;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.fxsql.dbclient.utils.ApplicationTheme;

public class MainController {
    public ToggleSwitch themeToggle;
    @FXML
    private Label welcomeText;

    private ApplicationTheme currentTheme;
    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }

    public void initialize(){
        //Initialize toggle switch
        currentTheme = ApplicationTheme.LIGHT;
        themeToggle.selectedProperty().addListener((obs,old,val)->{
            if(currentTheme == ApplicationTheme.LIGHT){
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                currentTheme = ApplicationTheme.DARK;
            }
            else{
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                currentTheme = ApplicationTheme.LIGHT;
            }
        });

    }
}