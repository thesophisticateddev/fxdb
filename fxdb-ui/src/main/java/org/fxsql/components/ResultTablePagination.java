package org.fxsql.components;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * A pagination wrapper for TableView that provides page navigation.
 */
public class ResultTablePagination<T> extends VBox {

    private static final int[] PAGE_SIZE_OPTIONS = {25, 50, 100, 200, 500};
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final TableView<T> tableView;
    private final HBox paginationBar;
    private final Label pageInfoLabel;
    private final Label totalRowsLabel;
    private final ComboBox<Integer> pageSizeCombo;
    private final Button firstButton;
    private final Button prevButton;
    private final Button nextButton;
    private final Button lastButton;
    private final TextField pageInput;

    private ObservableList<T> allData = FXCollections.observableArrayList();
    private final IntegerProperty currentPage = new SimpleIntegerProperty(1);
    private final IntegerProperty pageSize = new SimpleIntegerProperty(DEFAULT_PAGE_SIZE);
    private final IntegerProperty totalPages = new SimpleIntegerProperty(1);
    private final IntegerProperty totalRows = new SimpleIntegerProperty(0);

    public ResultTablePagination(TableView<T> tableView) {
        this.tableView = tableView;
        this.paginationBar = new HBox(10);
        this.pageInfoLabel = new Label();
        this.totalRowsLabel = new Label();
        this.pageSizeCombo = new ComboBox<>();
        this.firstButton = createIconButton(Feather.CHEVRONS_LEFT, "First page");
        this.prevButton = createIconButton(Feather.CHEVRON_LEFT, "Previous page");
        this.nextButton = createIconButton(Feather.CHEVRON_RIGHT, "Next page");
        this.lastButton = createIconButton(Feather.CHEVRONS_RIGHT, "Last page");
        this.pageInput = new TextField();

        setupUI();
        setupBindings();
    }

    private void setupUI() {
        // Page size selector
        pageSizeCombo.getItems().addAll(PAGE_SIZE_OPTIONS[0], PAGE_SIZE_OPTIONS[1],
                PAGE_SIZE_OPTIONS[2], PAGE_SIZE_OPTIONS[3], PAGE_SIZE_OPTIONS[4]);
        pageSizeCombo.setValue(DEFAULT_PAGE_SIZE);
        pageSizeCombo.setPrefWidth(80);

        // Page input
        pageInput.setPrefWidth(50);
        pageInput.setAlignment(Pos.CENTER);

        // Pagination bar layout
        paginationBar.setAlignment(Pos.CENTER_LEFT);
        paginationBar.setPadding(new Insets(5, 10, 5, 10));
        paginationBar.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        Label rowsLabel = new Label("Rows per page:");
        rowsLabel.setStyle("-fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        paginationBar.getChildren().addAll(
                totalRowsLabel,
                spacer,
                rowsLabel,
                pageSizeCombo,
                new Separator(),
                firstButton,
                prevButton,
                new Label("Page"),
                pageInput,
                new Label("of"),
                pageInfoLabel,
                nextButton,
                lastButton
        );

        // Style labels
        totalRowsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        pageInfoLabel.setStyle("-fx-font-size: 12px;");

        // Layout
        VBox.setVgrow(tableView, Priority.ALWAYS);
        this.getChildren().addAll(tableView, paginationBar);
        this.setSpacing(0);
    }

    private void setupBindings() {
        // Page size change
        pageSizeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                pageSize.set(newVal);
                currentPage.set(1);
                updateTableData();
            }
        });

        // Navigation buttons
        firstButton.setOnAction(e -> goToPage(1));
        prevButton.setOnAction(e -> goToPage(currentPage.get() - 1));
        nextButton.setOnAction(e -> goToPage(currentPage.get() + 1));
        lastButton.setOnAction(e -> goToPage(totalPages.get()));

        // Page input
        pageInput.setOnAction(e -> {
            try {
                int page = Integer.parseInt(pageInput.getText());
                goToPage(page);
            } catch (NumberFormatException ex) {
                pageInput.setText(String.valueOf(currentPage.get()));
            }
        });

        // Update button states when page changes
        currentPage.addListener((obs, oldVal, newVal) -> updateButtonStates());
        totalPages.addListener((obs, oldVal, newVal) -> updateButtonStates());

        // Update labels
        currentPage.addListener((obs, oldVal, newVal) -> updateLabels());
        totalPages.addListener((obs, oldVal, newVal) -> updateLabels());
        totalRows.addListener((obs, oldVal, newVal) -> updateLabels());
    }

    private Button createIconButton(Feather icon, String tooltip) {
        Button button = new Button();
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(14);
        button.setGraphic(fontIcon);
        button.setTooltip(new Tooltip(tooltip));
        button.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #e0e0e0; -fx-cursor: hand;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));
        return button;
    }

    /**
     * Sets the data to be paginated.
     */
    public void setData(List<T> data) {
        this.allData = FXCollections.observableArrayList(data);
        this.totalRows.set(data.size());
        this.currentPage.set(1);
        calculateTotalPages();
        updateTableData();
        updateButtonStates();
        updateLabels();
    }

    /**
     * Adds data to the existing dataset.
     */
    public void addData(List<T> data) {
        this.allData.addAll(data);
        this.totalRows.set(allData.size());
        calculateTotalPages();
        updateTableData();
        updateButtonStates();
        updateLabels();
    }

    /**
     * Clears all data.
     */
    public void clearData() {
        this.allData.clear();
        this.totalRows.set(0);
        this.currentPage.set(1);
        this.totalPages.set(1);
        tableView.getItems().clear();
        updateButtonStates();
        updateLabels();
    }

    private void calculateTotalPages() {
        int total = (int) Math.ceil((double) allData.size() / pageSize.get());
        totalPages.set(Math.max(1, total));
    }

    private void goToPage(int page) {
        if (page < 1) page = 1;
        if (page > totalPages.get()) page = totalPages.get();
        currentPage.set(page);
        updateTableData();
    }

    private void updateTableData() {
        int fromIndex = (currentPage.get() - 1) * pageSize.get();
        int toIndex = Math.min(fromIndex + pageSize.get(), allData.size());

        if (fromIndex >= allData.size()) {
            tableView.setItems(FXCollections.observableArrayList());
        } else {
            List<T> pageData = allData.subList(fromIndex, toIndex);
            tableView.setItems(FXCollections.observableArrayList(pageData));
        }
    }

    private void updateButtonStates() {
        int page = currentPage.get();
        int total = totalPages.get();

        firstButton.setDisable(page <= 1);
        prevButton.setDisable(page <= 1);
        nextButton.setDisable(page >= total);
        lastButton.setDisable(page >= total);
    }

    private void updateLabels() {
        pageInfoLabel.setText(String.valueOf(totalPages.get()));
        pageInput.setText(String.valueOf(currentPage.get()));

        int fromRow = allData.isEmpty() ? 0 : (currentPage.get() - 1) * pageSize.get() + 1;
        int toRow = Math.min(currentPage.get() * pageSize.get(), totalRows.get());

        totalRowsLabel.setText(String.format("Showing %d-%d of %d rows", fromRow, toRow, totalRows.get()));
    }

    /**
     * Returns the underlying TableView.
     */
    public TableView<T> getTableView() {
        return tableView;
    }

    /**
     * Returns the current page number.
     */
    public int getCurrentPage() {
        return currentPage.get();
    }

    /**
     * Returns the page size.
     */
    public int getPageSize() {
        return pageSize.get();
    }

    /**
     * Returns the total number of pages.
     */
    public int getTotalPages() {
        return totalPages.get();
    }

    /**
     * Returns the total number of rows.
     */
    public int getTotalRows() {
        return totalRows.get();
    }
}