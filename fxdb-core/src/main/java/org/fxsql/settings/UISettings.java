package org.fxsql.settings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class UISettings {
    private final String themeKey;
    private final double fontSize;
    private final String accentKey;
    private final String dockBorderMode;
    private final String dockBorderColor;

    @JsonCreator
    public UISettings(
            @JsonProperty("themeKey") String themeKey,
            @JsonProperty("fontSize") double fontSize,
            @JsonProperty("accentKey") String accentKey,
            @JsonProperty("dockBorderMode") String dockBorderMode,
            @JsonProperty("dockBorderColor") String dockBorderColor) {
        this.themeKey = themeKey != null ? themeKey : "PRIMER_LIGHT";
        this.fontSize = fontSize > 0 ? fontSize : 13.0;
        this.accentKey = accentKey != null ? accentKey : "GREEN";
        this.dockBorderMode = dockBorderMode != null ? dockBorderMode : "FOLLOW_ACCENT";
        this.dockBorderColor = dockBorderColor != null ? dockBorderColor : "#00A86B";
    }

    public String getThemeKey() { return themeKey; }
    public double getFontSize() { return fontSize; }
    public String getAccentKey() { return accentKey; }
    public String getDockBorderMode() { return dockBorderMode; }
    public String getDockBorderColor() { return dockBorderColor; }

    public UISettings withThemeKey(String key) {
        return new UISettings(key, fontSize, accentKey, dockBorderMode, dockBorderColor);
    }
    public UISettings withFontSize(double size) {
        return new UISettings(themeKey, size, accentKey, dockBorderMode, dockBorderColor);
    }
    public UISettings withAccentKey(String key) {
        return new UISettings(themeKey, fontSize, key, dockBorderMode, dockBorderColor);
    }
    public UISettings withDockBorderMode(String mode) {
        return new UISettings(themeKey, fontSize, accentKey, mode, dockBorderColor);
    }
    public UISettings withDockBorderColor(String color) {
        return new UISettings(themeKey, fontSize, accentKey, dockBorderMode, color);
    }
}