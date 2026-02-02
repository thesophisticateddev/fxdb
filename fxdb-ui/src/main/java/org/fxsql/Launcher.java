package org.fxsql;

/**
 * Launcher class for the FXDB application.
 *
 * This class is needed for running JavaFX applications from a fat/shaded JAR.
 * When the main class extends Application, JavaFX performs module checks that
 * fail when running from a non-modular JAR. By using a separate launcher class
 * that doesn't extend Application, we bypass these checks.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApplication.main(args);
    }
}