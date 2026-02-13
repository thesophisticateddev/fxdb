package org.fxsql.components;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A pane that displays application information and release notes.
 * Shown as the default tab when the application starts.
 */
public class AboutPane extends VBox {

    private static final String APP_NAME = "FXDB";
    private static final String APP_VERSION = "1.0.0";
    private static final String APP_DESCRIPTION = "A lightweight, cross-platform database management tool built with JavaFX.";

    private static final String[][] RELEASE_NOTES = {
            {"Trigger controller added", "Added UI for creating database triggers with validation and SQL preview."},
            {"JavaFX unnecessary modules removed", "Reduced application footprint by removing unused JavaFX modules."},
            {"Add release workflow", "CI/CD pipeline for automated builds and releases."},
            {"Adding the MIT license", "Project is now open-source under the MIT license."},
            {"Build fixes for Windows/macOS", "Resolved cross-platform build issues for distributable packages."},
            {"Long running query execution on separate thread", "Moved query execution off the UI thread to keep the interface responsive."},
            {"Workspace support", "Save and restore workspace state across connection switches."},
            {"Plugin system", "Extensible plugin architecture with documentation and sample plugins."},
            {"Connection password encryption", "Database connection passwords are now stored encrypted."},
            {"UI refactor", "Modernized the main interface with floating tabs and improved layout."},
            {"SQL editor updates", "Enhanced SQL script editor with syntax support and file management."},
            {"PostgreSQL support", "Full support for PostgreSQL databases including triggers and functions."},
            {"Driver loading fix for SQLite", "Resolved JDBC driver loading issues for SQLite connections."},
            {"MySQL support", "Added MySQL database connection and query support."},
            {"Notification system", "Floating toast notifications for success, error, and info messages."},
            {"SQL error alerts", "Detailed error dialogs with stack traces for failed queries."},
            {"SQL editor added", "Integrated SQL script editor with execute, save, and open capabilities."},
            {"Multiple database connections", "Manage and switch between multiple database connections."},
            {"Driver load notification on UI", "Visual progress indicator for JDBC driver loading on startup."},
            {"Table data reading", "Browse and view table data with pagination support."},
    };

    public AboutPane() {
        setupUI();
    }

    private void setupUI() {
        this.setSpacing(0);
        this.setAlignment(Pos.TOP_CENTER);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(20);
        content.setPadding(new Insets(30, 40, 30, 40));
        content.setMaxWidth(700);
        content.setAlignment(Pos.TOP_LEFT);

        // Header section
        content.getChildren().add(createHeader());
        content.getChildren().add(new Separator());

        // Supported databases
        content.getChildren().add(createSupportedDatabases());
        content.getChildren().add(new Separator());

        // Release notes section
        content.getChildren().add(createReleaseNotes());

        // Center the content in the scroll pane
        VBox wrapper = new VBox();
        wrapper.setAlignment(Pos.TOP_CENTER);
        wrapper.getChildren().add(content);

        scrollPane.setContent(wrapper);
        this.getChildren().add(scrollPane);
    }

    private VBox createHeader() {
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        FontIcon appIcon = new FontIcon(Feather.DATABASE);
        appIcon.setIconSize(28);

        Label title = new Label(APP_NAME);
        title.getStyleClass().addAll(Styles.TITLE_2);

        Label version = new Label("v" + APP_VERSION);
        version.setStyle("-fx-font-size: 14px; -fx-text-fill: #666; -fx-padding: 4 0 0 0;");

        titleRow.getChildren().addAll(appIcon, title, version);

        Label description = new Label(APP_DESCRIPTION);
        description.setStyle("-fx-font-size: 13px; -fx-text-fill: #555;");
        description.setWrapText(true);

        Label getStarted = new Label("Select a database connection from the dropdown on the left to get started.");
        getStarted.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        getStarted.setWrapText(true);

        header.getChildren().addAll(titleRow, description, getStarted);
        return header;
    }

    private VBox createSupportedDatabases() {
        VBox section = new VBox(8);

        Label sectionTitle = new Label("Supported Databases");
        sectionTitle.getStyleClass().addAll(Styles.TITLE_4);

        HBox databases = new HBox(20);
        databases.setAlignment(Pos.CENTER_LEFT);
        databases.setPadding(new Insets(5, 0, 0, 0));

        databases.getChildren().addAll(
                createDatabaseBadge("PostgreSQL"),
                createDatabaseBadge("MySQL"),
                createDatabaseBadge("SQLite"),
                createDatabaseBadge("MariaDB"),
                createDatabaseBadge("Generic JDBC")
        );

        section.getChildren().addAll(sectionTitle, databases);
        return section;
    }

    private Label createDatabaseBadge(String name) {
        Label badge = new Label(name);
        badge.setStyle(
                "-fx-background-color: #e8f0fe; -fx-text-fill: #1a73e8; " +
                "-fx-padding: 4 10; -fx-background-radius: 12; -fx-font-size: 12px;"
        );
        return badge;
    }

    private VBox createReleaseNotes() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Release Notes");
        sectionTitle.getStyleClass().addAll(Styles.TITLE_4);

        section.getChildren().add(sectionTitle);

        for (String[] entry : RELEASE_NOTES) {
            section.getChildren().add(createReleaseEntry(entry[0], entry[1]));
        }

        return section;
    }

    private HBox createReleaseEntry(String title, String description) {
        HBox entry = new HBox(10);
        entry.setAlignment(Pos.TOP_LEFT);
        entry.setPadding(new Insets(2, 0, 2, 0));

        FontIcon bullet = new FontIcon(Feather.CHECK_CIRCLE);
        bullet.setIconSize(14);
        bullet.setStyle("-fx-icon-color: #4CAF50; -fx-padding: 2 0 0 0;");

        VBox textBox = new VBox(2);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        titleLabel.setWrapText(true);

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        descLabel.setWrapText(true);

        textBox.getChildren().addAll(titleLabel, descLabel);
        entry.getChildren().addAll(bullet, textBox);
        return entry;
    }
}
