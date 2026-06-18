package org.fxsql.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Central animation factory.
 *
 * All animations are {@link Timeline}s using {@link Interpolator#EASE_BOTH}
 * with durations clamped to 150–500ms, and only animate opacity and
 * translation — never layout-affecting properties — so they run on the GPU
 * without triggering layout passes.
 *
 * While an animation plays, the node is cached ({@code setCache(true)} +
 * {@link CacheHint#SPEED}); the hint is restored when it finishes.
 *
 * When the OS reduced-motion preference is detected (or forced with
 * {@code -Dfxdb.reducedMotion=true}), animations jump straight to their end
 * state.
 */
public final class AnimationFactory {

    private static final Logger logger = Logger.getLogger(AnimationFactory.class.getName());

    public static final Duration FAST = Duration.millis(150);
    public static final Duration NORMAL = Duration.millis(300);
    public static final Duration SLOW = Duration.millis(500);

    private static final double MIN_MILLIS = 150;
    private static final double MAX_MILLIS = 500;

    private static volatile Boolean reducedMotion;

    private AnimationFactory() {
    }

    public static Timeline fadeIn(Node node, Duration duration) {
        node.setOpacity(0);
        return build(node, duration, new KeyValue(node.opacityProperty(), 1, Interpolator.EASE_BOTH));
    }

    public static Timeline fadeOut(Node node, Duration duration) {
        return build(node, duration, new KeyValue(node.opacityProperty(), 0, Interpolator.EASE_BOTH));
    }

    /** Slide in horizontally from {@code fromX} to 0 while fading in. */
    public static Timeline slideInX(Node node, double fromX, Duration duration) {
        node.setTranslateX(fromX);
        node.setOpacity(0);
        return build(node, duration,
                new KeyValue(node.translateXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(node.opacityProperty(), 1, Interpolator.EASE_BOTH));
    }

    /** Slide out horizontally to {@code toX} while fading out. */
    public static Timeline slideOutX(Node node, double toX, Duration duration) {
        return build(node, duration,
                new KeyValue(node.translateXProperty(), toX, Interpolator.EASE_BOTH),
                new KeyValue(node.opacityProperty(), 0, Interpolator.EASE_BOTH));
    }

    private static Timeline build(Node node, Duration duration, KeyValue... values) {
        Timeline timeline = new Timeline(new KeyFrame(clamp(duration), values));

        CacheHint previousHint = node.getCacheHint();
        boolean previousCache = node.isCache();
        node.setCache(true);
        node.setCacheHint(CacheHint.SPEED);
        timeline.statusProperty().addListener((obs, old, status) -> {
            if (status == Animation.Status.STOPPED) {
                node.setCacheHint(previousHint);
                node.setCache(previousCache);
            }
        });

        if (isReducedMotion()) {
            // Jump to the end state: play the last frame instantly.
            timeline.setRate(timeline.getCycleDuration().toMillis()); // effectively instant
        }
        return timeline;
    }

    private static Duration clamp(Duration duration) {
        double ms = duration == null ? NORMAL.toMillis() : duration.toMillis();
        return Duration.millis(Math.max(MIN_MILLIS, Math.min(MAX_MILLIS, ms)));
    }

    /**
     * Best-effort OS reduced-motion detection, cached for the process
     * lifetime. Overridable with {@code -Dfxdb.reducedMotion=true|false}.
     */
    public static boolean isReducedMotion() {
        Boolean cached = reducedMotion;
        if (cached != null) {
            return cached;
        }
        synchronized (AnimationFactory.class) {
            if (reducedMotion == null) {
                reducedMotion = detectReducedMotion();
                logger.fine("Reduced motion: " + reducedMotion);
            }
            return reducedMotion;
        }
    }

    private static boolean detectReducedMotion() {
        String override = System.getProperty("fxdb.reducedMotion");
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("linux")) {
                // GNOME exposes the preference via gsettings; "false" means animations off.
                String out = exec("gsettings", "get", "org.gnome.desktop.interface", "enable-animations");
                return out != null && out.trim().equals("false");
            }
            if (os.contains("mac")) {
                String out = exec("defaults", "read", "com.apple.universalaccess", "reduceMotion");
                return out != null && out.trim().equals("1");
            }
            if (os.contains("win")) {
                String out = exec("reg", "query",
                        "HKCU\\Control Panel\\Desktop\\WindowMetrics", "/v", "MinAnimate");
                return out != null && out.contains("0x0");
            }
        } catch (Exception e) {
            logger.fine("Reduced-motion detection failed: " + e.getMessage());
        }
        return false;
    }

    private static String exec(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return null;
        }
        return process.exitValue() == 0 ? output : null;
    }
}
