package org.fxsql.components.sqlScriptExecutor;

import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLEditor extends StackPane {
    private static final Pattern KEYWORD_PATTERN =
            Pattern.compile("\\b(SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|JOIN)\\b", Pattern.CASE_INSENSITIVE);
    private final CodeArea codeArea;

    public SQLEditor() {
        this.codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            codeArea.setStyleSpans(0, computeHighlighting(newText));
        });
        this.getChildren().add(codeArea);
        try {
            this.getStylesheets()
                    .add(Objects.requireNonNull(getClass().getClassLoader().getResource("stylesheets/sql-editor.css"))
                            .toExternalForm());
        }
        catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = KEYWORD_PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton("keyword"), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    public String[] sqlQueriesInEditor() {
        String sqlText = codeArea.getText();

        String[] queries = sqlText.split(";");

        for (String query : queries) {
            query.trim();
        }
        return queries;
    }
}

