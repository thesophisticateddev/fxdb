package org.fxsql.controller;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.fxsql.settings.AccentColor;
import org.fxsql.settings.AppTheme;
import org.fxsql.settings.UISettings;
import org.fxsql.settings.UISettingsService;
import javafx.collections.FXCollections;

public class SettingsController {

    @Inject
    private UISettingsService service;

    @FXML private ComboBox<AppTheme> themeCombo;
    @FXML private ComboBox<Double> fontSizeCombo;
    @FXML private ComboBox<AccentColor> accentCombo;
    @FXML private RadioButton followAccentRadio;
    @FXML private RadioButton customBorderRadio;
    @FXML private ColorPicker borderColorPicker;
    @FXML private Button applyBtn;
    @FXML private Button resetBtn;
    @FXML private Button closeBtn;

    @FXML
    public void initialize() {
        themeCombo.setItems(FXCollections.observableArrayList(AppTheme.values()));
        fontSizeCombo.setItems(FXCollections.observableArrayList(12.0,13.0,15.0,17.0));
        accentCombo.setItems(FXCollections.observableArrayList(AccentColor.values()));

        UISettings s = service.getCurrent();
        themeCombo.getSelectionModel().select(AppTheme.valueOf(s.getThemeKey()));
        fontSizeCombo.getSelectionModel().select(s.getFontSize());
        accentCombo.getSelectionModel().select(AccentColor.valueOf(s.getAccentKey()));
        boolean follow = "FOLLOW_ACCENT".equals(s.getDockBorderMode());
        followAccentRadio.setSelected(follow);
        customBorderRadio.setSelected(!follow);
        borderColorPicker.setValue(javafx.scene.paint.Color.web(s.getDockBorderColor()));

        themeCombo.valueProperty().addListener((o,ov,nv) -> applyLive());
        fontSizeCombo.valueProperty().addListener((o,ov,nv) -> applyLive());
        accentCombo.valueProperty().addListener((o,ov,nv) -> applyLive());
        followAccentRadio.selectedProperty().addListener((o,ov,nv) -> applyLive());
        customBorderRadio.selectedProperty().addListener((o,ov,nv) -> applyLive());
        borderColorPicker.valueProperty().addListener((o,ov,nv) -> applyLive());

        applyBtn.setOnAction(e -> applyLive());
        resetBtn.setOnAction(e -> resetDefaults());
        closeBtn.setOnAction(e -> ((Stage)closeBtn.getScene().getWindow()).close());
    }

    private void applyLive() {
        UISettings s = service.getCurrent()
            .withThemeKey(themeCombo.getValue().name())
            .withFontSize(fontSizeCombo.getValue())
            .withAccentKey(accentCombo.getValue().name())
            .withDockBorderMode(followAccentRadio.isSelected() ? "FOLLOW_ACCENT" : "CUSTOM")
            .withDockBorderColor(String.format("#%02X%02X%02X",
                (int)(borderColorPicker.getValue().getRed()*255),
                (int)(borderColorPicker.getValue().getGreen()*255),
                (int)(borderColorPicker.getValue().getBlue()*255)));
        service.save(s);
    }

    private void resetDefaults() {
        UISettings def = new UISettings(null,0,null,null,null);
        service.save(def);
        initialize();
    }
}