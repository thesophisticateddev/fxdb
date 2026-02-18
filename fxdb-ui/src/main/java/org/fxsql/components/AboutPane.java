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
import org.fxsql.model.ReleaseNote;
import org.fxsql.service.ReleaseNotesLoader;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * A pane that displays application information and release notes.
 * Shown as the default tab when the application starts.
 */
public class AboutPane extends VBox {

    private static final String APP_NAME = "FXDB";
    private static final String APP_DESCRIPTION = "A lightweight, cross-platform database management tool built with JavaFX.";

    private final String appVersion;
    private final List<ReleaseNote> releaseNotes;

    public AboutPane() {
        this.appVersion = ReleaseNotesLoader.loadVersion();
        this.releaseNotes = ReleaseNotesLoader.load();
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

        Label version = new Label(appVersion.startsWith("v") ? appVersion : "v" + appVersion);
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

        for (ReleaseNote note : releaseNotes) {
            String subtitle = note.getHash() + " — " + note.getDate();
            section.getChildren().add(createReleaseEntry(note.getSubject(), subtitle));
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
