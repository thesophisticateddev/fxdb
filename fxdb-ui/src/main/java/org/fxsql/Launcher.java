package org.fxsql;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Launcher class for the FXDB application.
 *
 * This class is needed for running JavaFX applications from a fat/shaded JAR.
 * When the main class extends Application, JavaFX performs module checks that
 * fail when running from a non-modular JAR. By using a separate launcher class
 * that doesn't extend Application, we bypass these checks.
 *
 * On Linux, it also ensures an X11 display is available before starting JavaFX.
 * JavaFX 17 does not support native Wayland — it requires X11 (or XWayland).
 * If DISPLAY is not set, the launcher detects the correct display and xauth
 * file, then re-launches with the proper environment.
 */
public class Launcher {

    private static final String DISPLAY_RELAUNCH_FLAG = "fxdb.display.relaunched";
    private static final String[] CANDIDATE_DISPLAYS = {":0", ":1", ":2"};

    public static void main(String[] args) {
        if (needsDisplayFix()) {
            relaunchWithDisplay(args);
            return;
        }
        MainApplication.main(args);
    }

    private static boolean needsDisplayFix() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) {
            return false;
        }

        if ("true".equals(System.getProperty(DISPLAY_RELAUNCH_FLAG))) {
            return false;
        }

        String display = System.getenv("DISPLAY");
        return display == null || display.isEmpty();
    }

    private static void relaunchWithDisplay(String[] args) {
        // Detect XAUTHORITY — needed for X11 authentication
        String xauthority = System.getenv("XAUTHORITY");
        if (xauthority == null || xauthority.isEmpty() || !new File(xauthority).exists()) {
            xauthority = findXauthorityFile();
        }

        // Detect DISPLAY — scan X11 sockets or fall back to candidates
        String display = detectXServerSocket();
        if (display == null) {
            display = findWorkingDisplay(xauthority);
        }

        if (display == null) {
            System.err.println(
                "ERROR: No X11 display server detected. FXDB requires a graphical environment.\n" +
                "If running under Wayland, ensure XWayland is enabled and set:\n" +
                "  export DISPLAY=:0\n" +
                "Or run with: DISPLAY=:0 java -jar fxdb.jar"
            );
            System.exit(1);
            return;
        }

        System.err.println("DISPLAY not set, relaunching with DISPLAY=" + display
                + (xauthority != null ? " XAUTHORITY=" + xauthority : ""));

        List<String> command = buildCommand(args);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("DISPLAY", display);
            if (xauthority != null) {
                pb.environment().put("XAUTHORITY", xauthority);
            }
            pb.inheritIO();
            Process process = pb.start();
            System.exit(process.waitFor());
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to relaunch — " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Scans /tmp/.X11-unix/ for X server sockets and returns a usable
     * display string (e.g. ":0" or ":1"). Prefers sockets owned by the
     * current user over root-owned sockets. Returns null if not visible
     * (e.g. inside a Flatpak sandbox).
     */
    private static String detectXServerSocket() {
        File x11Dir = new File("/tmp/.X11-unix");
        if (!x11Dir.isDirectory()) {
            return null;
        }
        File[] sockets = x11Dir.listFiles((dir, name) -> name.startsWith("X"));
        if (sockets == null || sockets.length == 0) {
            return null;
        }

        String userName = System.getProperty("user.name");
        String userOwned = null;
        String fallback = null;
        for (File socket : sockets) {
            String candidate = ":" + socket.getName().substring(1);
            try {
                String owner = Files.getOwner(socket.toPath()).getName();
                if (userName != null && userName.equals(owner)) {
                    if (userOwned == null || candidate.compareTo(userOwned) < 0) {
                        userOwned = candidate;
                    }
                    continue;
                }
            } catch (IOException ignored) {}
            if (fallback == null || candidate.compareTo(fallback) < 0) {
                fallback = candidate;
            }
        }
        return userOwned != null ? userOwned : fallback;
    }

    /**
     * Searches common locations for the Xauthority file.
     * On Wayland with XWayland, it's typically at /run/user/UID/xauth_*.
     */
    private static String findXauthorityFile() {
        // Check ~/.Xauthority
        String home = System.getProperty("user.home");
        File homeXauth = new File(home, ".Xauthority");
        if (homeXauth.exists()) {
            return homeXauth.getAbsolutePath();
        }

        // Check XDG_RUNTIME_DIR for xauth_* files (common on Wayland/XWayland)
        String runtimeDir = System.getenv("XDG_RUNTIME_DIR");
        if (runtimeDir == null) {
            // Fallback to /run/user/<uid>
            runtimeDir = "/run/user/" + getUid();
        }

        File rtDir = new File(runtimeDir);
        if (rtDir.isDirectory()) {
            try (Stream<Path> files = Files.list(rtDir.toPath())) {
                return files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("xauth_") || name.startsWith(".mutter-Xwaylandauth.");
                    })
                    .map(Path::toString)
                    .findFirst()
                    .orElse(null);
            } catch (IOException ignored) {}
        }

        return null;
    }

    /**
     * When X11 sockets aren't visible (Flatpak), probe candidate displays
     * by checking if xdpyinfo can connect.
     */
    private static String findWorkingDisplay(String xauthority) {
        for (String display : CANDIDATE_DISPLAYS) {
            if (probeDisplay(display, xauthority)) {
                return display;
            }
        }
        // If probing failed (xdpyinfo not available), fall back to :0 on Wayland
        String wayland = System.getenv("WAYLAND_DISPLAY");
        if (wayland != null && !wayland.isEmpty()) {
            return ":0";
        }
        return null;
    }

    /**
     * Tries to connect to a display using xdpyinfo to verify it's usable.
     */
    private static boolean probeDisplay(String display, String xauthority) {
        try {
            ProcessBuilder pb = new ProcessBuilder("xdpyinfo", "-display", display);
            pb.environment().put("DISPLAY", display);
            if (xauthority != null) {
                pb.environment().put("XAUTHORITY", xauthority);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes(); // consume output
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static List<String> buildCommand(String[] args) {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-D" + DISPLAY_RELAUNCH_FLAG + "=true");
        command.add("-cp");
        command.add(classpath);

        for (String prop : new String[]{"fxdb.dev"}) {
            String value = System.getProperty(prop);
            if (value != null) {
                command.add("-D" + prop + "=" + value);
            }
        }

        command.add(Launcher.class.getName());
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    private static String getUid() {
        try {
            Process p = new ProcessBuilder("id", "-u").start();
            String uid = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return uid;
        } catch (IOException | InterruptedException e) {
            return "1000";
        }
    }
}
