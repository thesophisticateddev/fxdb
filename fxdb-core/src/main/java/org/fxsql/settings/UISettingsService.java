package org.fxsql.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Singleton;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import org.fxsql.config.AppPaths;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class UISettingsService {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ObjectProperty<UISettings> settingsProperty = new SimpleObjectProperty<>();
    private final List<WeakReference<Scene>> scenes = new CopyOnWriteArrayList<>();
    private UISettings current;

    public UISettingsService() {
        load();
    }

    public ObjectProperty<UISettings> settingsProperty() { return settingsProperty; }
    public UISettings getCurrent() { return current; }

    public void registerScene(Scene scene) {
        scenes.add(new WeakReference<>(scene));
        applyToScene(scene);
    }

    public void applyAll() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::applyAll);
            return;
        }
        AppTheme theme = resolveTheme(current.getThemeKey());
        Application.setUserAgentStylesheet(theme.createTheme().getUserAgentStylesheet());
        String style = buildRootStyle(current);
        for (WeakReference<Scene> ref : new ArrayList<>(scenes)) {
            Scene s = ref.get();
            if (s != null) {
                s.getRoot().setStyle(style);
            } else {
                scenes.remove(ref);
            }
        }
    }

    private void applyToScene(Scene scene) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> applyToScene(scene));
            return;
        }
        scene.getRoot().setStyle(buildRootStyle(current));
    }

    public String buildRootStyle(UISettings s) {
        AccentColor accent = resolveAccent(s.getAccentKey());
        String border = "FOLLOW_ACCENT".equals(s.getDockBorderMode()) ? accent.getEmphasis() : s.getDockBorderColor();
        return String.format("-fx-font-size: %.1fpx; -color-accent-fg: %s; -color-accent-emphasis: %s; -color-accent-muted: %s; -color-accent-subtle: %s; -dockfx-border-color: %s;",
                s.getFontSize(), accent.getFg(), accent.getEmphasis(), accent.getMuted(), accent.getSubtle(), border);
    }

    private AppTheme resolveTheme(String key) {
        try { return AppTheme.valueOf(key); } catch (Exception e) { return AppTheme.PRIMER_LIGHT; }
    }
    private AccentColor resolveAccent(String key) {
        try { return AccentColor.valueOf(key); } catch (Exception e) { return AccentColor.GREEN; }
    }

    private void load() {
        File f = AppPaths.getFile("settings", "ui.json");
        if (f.exists()) {
            try {
                current = mapper.readValue(f, UISettings.class);
            } catch (Exception e) {
                current = new UISettings(null, 0, null, null, null);
            }
        } else {
            current = new UISettings(null, 0, null, null, null);
        }
        settingsProperty.set(current);
    }

    public void save(UISettings s) {
        current = s;
        settingsProperty.set(s);
        File f = AppPaths.getFile("settings", "ui.json");
        try {
            mapper.writeValue(f, s);
        } catch (Exception ignored) {}
        applyAll();
    }
}