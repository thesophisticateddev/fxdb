package org.fxsql.ui;

import javafx.scene.Scene;
import javafx.scene.text.Font;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Central typography system.
 *
 * Selects the platform system font family — Segoe UI on Windows, SF Pro on
 * macOS, Noto Sans on Linux — and installs the root stylesheet that applies
 * it (with LCD font smoothing) to every scene. Sizes follow a fixed scale:
 * Heading 24px, Body 14px, Caption 12px, exposed as both constants and the
 * CSS classes {@code fxdb-heading}, {@code fxdb-body}, {@code fxdb-caption}.
 *
 * Note: the base font size itself is user-configurable through
 * UISettingsService, which sets {@code -fx-font-size} on the scene root.
 * This class deliberately only controls family, smoothing and the named
 * scale classes so the two systems do not fight.
 */
public final class FontManager {

    public static final double HEADING_SIZE = 24;
    public static final double BODY_SIZE = 14;
    public static final double CAPTION_SIZE = 12;

    public static final String HEADING_CLASS = "fxdb-heading";
    public static final String BODY_CLASS = "fxdb-body";
    public static final String CAPTION_CLASS = "fxdb-caption";

    private static final String ROOT_STYLESHEET = "stylesheets/root.css";

    private FontManager() {
    }

    /** The platform system font family, falling back to the JavaFX default. */
    public static String systemFontFamily() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String preferred;
        if (os.contains("win")) {
            preferred = "Segoe UI";
        } else if (os.contains("mac")) {
            preferred = "SF Pro";
        } else {
            preferred = "Noto Sans";
        }
        return Font.getFamilies().contains(preferred) ? preferred : Font.getDefault().getFamily();
    }

    /**
     * Adds the root typography stylesheet to a scene. Safe to call for every
     * window — duplicates are skipped.
     *
     * The font family is resolved at runtime (so a missing platform font
     * falls back cleanly) and injected as a data-URI stylesheet rather than
     * an inline root style, because UISettingsService replaces the root
     * inline style wholesale when the user changes theme/font settings.
     */
    public static void applyTo(Scene scene) {
        String css = Objects.requireNonNull(
                FontManager.class.getClassLoader().getResource(ROOT_STYLESHEET),
                "Missing " + ROOT_STYLESHEET
        ).toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
        String familySheet = familyStylesheet();
        if (!scene.getStylesheets().contains(familySheet)) {
            scene.getStylesheets().add(familySheet);
        }
    }

    private static volatile String cachedFamilySheet;

    private static String familyStylesheet() {
        String sheet = cachedFamilySheet;
        if (sheet == null) {
            String css = ".root { -fx-font-family: \"" + systemFontFamily() + "\"; }";
            sheet = "data:text/css;base64," + Base64.getEncoder()
                    .encodeToString(css.getBytes(StandardCharsets.UTF_8));
            cachedFamilySheet = sheet;
        }
        return sheet;
    }
}
