package org.fxsql.ui;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.SnapshotParameters;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Centralized icon factory for the whole application.
 *
 * Standard icons are 24px glyphs centered inside a fixed 40x40
 * {@link StackPane} so that icons never shift surrounding layout and always
 * align identically across views. Size-constrained contexts (tab headers,
 * menu items, inline buttons) use the smaller named variants below so row
 * heights stay stable.
 */
public final class IconFactory {

    /** Standard glyph size inside a {@link #standard(Ikon)} container. */
    public static final int STANDARD_ICON_SIZE = 24;
    /** Fixed square side of the standard icon container. */
    public static final double CONTAINER_SIZE = 40;

    /** Glyph size for tab headers. */
    public static final int TAB_ICON_SIZE = 12;
    /** Glyph size for menu items and inline labels. */
    public static final int MENU_ICON_SIZE = 14;
    /** Glyph size for toolbar/side-panel buttons. */
    public static final int BUTTON_ICON_SIZE = 16;

    private IconFactory() {
    }

    /**
     * A 24px icon centered in a fixed 40x40 container. The fixed bounds keep
     * the icon from affecting layout when swapped, animated, or styled.
     */
    public static StackPane standard(Ikon ikon) {
        FontIcon icon = icon(ikon, STANDARD_ICON_SIZE);
        StackPane container = new StackPane(icon);
        container.setAlignment(Pos.CENTER);
        container.setMinSize(CONTAINER_SIZE, CONTAINER_SIZE);
        container.setPrefSize(CONTAINER_SIZE, CONTAINER_SIZE);
        container.setMaxSize(CONTAINER_SIZE, CONTAINER_SIZE);
        return container;
    }

    /** Icon for tab headers (12px). */
    public static FontIcon tabIcon(Ikon ikon) {
        return icon(ikon, TAB_ICON_SIZE);
    }

    /** Icon for menu items and inline labels (14px). */
    public static FontIcon menuIcon(Ikon ikon) {
        return icon(ikon, MENU_ICON_SIZE);
    }

    /** Icon for toolbar and side-panel buttons (16px). */
    public static FontIcon buttonIcon(Ikon ikon) {
        return icon(ikon, BUTTON_ICON_SIZE);
    }

    /** Single creation point for every {@link FontIcon} in the application. */
    public static FontIcon icon(Ikon ikon, int size) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(size);
        return icon;
    }

    /**
     * Pre-renders a complex static node to an image so the scene graph only
     * pays its rendering cost once. Intended for layered/composite static
     * graphics; single Feather glyphs render cheaply and do not need this.
     * Must be called on the FX thread.
     */
    public static ImageView snapshotStatic(javafx.scene.Node node) {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        Image image = node.snapshot(params, null);
        ImageView view = new ImageView(image);
        view.setSmooth(true);
        return view;
    }
}
