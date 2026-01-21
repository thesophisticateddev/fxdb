package org.fxsql.components.common;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import java.util.function.UnaryOperator;

public class NumericField extends TextField {

    public NumericField() {
        super();
        applyNumericFilter();
    }

    private void applyNumericFilter() {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String newText = change.getControlNewText();
            // Regex to allow only digits
            if (newText.matches("\\d*")) {
                return change;
            }
            return null;
        };

        this.setTextFormatter(new TextFormatter<>(filter));
    }
}