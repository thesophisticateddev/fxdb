package org.fxsql.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class DriverInstallController {

    @FXML private ComboBox<DriverCandidate> driverCombo;

    @FXML private TextField driverField;
    @FXML private TextField classNameField;
    @FXML private TextField jarPathField;
    @FXML private TextField downloadLinkField;

    @FXML private Label validationLabel;
    @FXML private Button installButton;

    private Stage stage;
    private Consumer<DriverCandidate> onInstall;

    // Simple “looks like a fully-qualified Java class name”
    private static final Pattern JAVA_FQCN =
            Pattern.compile("([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[A-Za-z_$][A-Za-z\\d_$]*");

    public void init(Stage stage,
                     List<DriverCandidate> candidates,
                     Consumer<DriverCandidate> onInstall) {

        this.stage = stage;
        this.onInstall = onInstall;

        driverCombo.setItems(FXCollections.observableArrayList(candidates));

        driverCombo.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(DriverCandidate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.driver());
            }
        });
        driverCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(DriverCandidate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.driver());
            }
        });

        // When selecting a driver, populate fields (editable so user can adjust)
        driverCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                driverField.clear();
                classNameField.clear();
                jarPathField.clear();
                downloadLinkField.clear();
            } else {
                driverField.setText(nvl(newV.driver()));
                classNameField.setText(nvl(newV.className()));
                jarPathField.setText(nvl(newV.jarPath()));
                downloadLinkField.setText(nvl(newV.downloadLink()));
            }
            clearValidation();
            updateInstallEnabled();
        });

        // Validate live as user types
        driverField.textProperty().addListener((o,a,b) -> { clearValidation(); updateInstallEnabled(); });
        classNameField.textProperty().addListener((o,a,b) -> { clearValidation(); updateInstallEnabled(); });
        jarPathField.textProperty().addListener((o,a,b) -> { clearValidation(); updateInstallEnabled(); });
        downloadLinkField.textProperty().addListener((o,a,b) -> { clearValidation(); updateInstallEnabled(); });

        updateInstallEnabled();
    }

    @FXML
    private void onInstall() {
        ValidationResult result = validateAll();
        if (!result.ok().isValid) {
            showValidation(result.message());
            return;
        }

        DriverCandidate selected = new DriverCandidate(
                driverField.getText().trim(),
                classNameField.getText().trim(),
                jarPathField.getText().trim(),
                downloadLinkField.getText().trim()
        );

        if (onInstall != null) {
            onInstall.accept(selected);
        }
        stage.close();
    }

    @FXML
    private void onCancel() {
        stage.close();
    }

    private void updateInstallEnabled() {
        installButton.setDisable(!validateAll().ok().isValid);
    }

    private ValidationResult validateAll() {
        // Required checks
        String driver = trimToNull(driverField.getText());
        if (driver == null) return ValidationResult.fail("Driver is required.");

        String className = trimToNull(classNameField.getText());
        if (className == null) return ValidationResult.fail("Driver className is required.");
        if (!JAVA_FQCN.matcher(className).matches()) {
            return ValidationResult.fail("Driver className must be a valid Java class name (e.g., org.postgresql.Driver).");
        }

        String jarPath = trimToNull(jarPathField.getText());
        if (jarPath == null) return ValidationResult.fail("JAR file path is required.");
        if (!jarPath.toLowerCase().endsWith(".jar")) {
            return ValidationResult.fail("JAR file path must point to a .jar file.");
        }

        // Optional stronger check: must exist on disk
        // (Enable if you want local jar selection/validation)
        /*
        try {
            Path p = Path.of(jarPath);
            if (!Files.exists(p) || Files.isDirectory(p)) {
                return ValidationResult.fail("JAR file path must exist and point to a file.");
            }
        } catch (Exception ex) {
            return ValidationResult.fail("JAR file path is not a valid path.");
        }
        */

        String downloadLink = trimToNull(downloadLinkField.getText());
        if (downloadLink == null) return ValidationResult.fail("Download link is required.");
        try {
            URI uri = URI.create(downloadLink);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return ValidationResult.fail("Download link must start with http:// or https://");
            }
            if (uri.getHost() == null) {
                return ValidationResult.fail("Download link must be a valid URL.");
            }
        } catch (Exception e) {
            return ValidationResult.fail("Download link must be a valid URL.");
        }

        return ValidationResult.ok();
    }

    private void showValidation(String msg) {
        validationLabel.setText(msg);
        validationLabel.setVisible(true);

        // Add simple red border highlight to all fields; you can make this smarter per-field if desired.
        setInvalid(driverField, true);
        setInvalid(classNameField, true);
        setInvalid(jarPathField, true);
        setInvalid(downloadLinkField, true);
    }

    private void clearValidation() {
        validationLabel.setText("");
        validationLabel.setVisible(false);
        setInvalid(driverField, false);
        setInvalid(classNameField, false);
        setInvalid(jarPathField, false);
        setInvalid(downloadLinkField, false);
    }

    private void setInvalid(TextField field, boolean invalid) {
        if (invalid) {
            field.setStyle("-fx-border-color: #b00020; -fx-border-width: 1.2; -fx-border-radius: 3;");
        } else {
            field.setStyle(""); // reset
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nvl(String s) {
        return Objects.requireNonNullElse(s, "");
    }

    private record ValidationResult(boolean isValid, String message) {
        static ValidationResult ok() { return new ValidationResult(true, ""); }
        static ValidationResult fail(String msg) { return new ValidationResult(false, msg); }
    }

    /**
     * Your selectable model.
     * Swap this with your real DriverReference if you want.
     */
    public record DriverCandidate(
            String driver,
            String className,
            String jarPath,
            String downloadLink
    ) {}
}

