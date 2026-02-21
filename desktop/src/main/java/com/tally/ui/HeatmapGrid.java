package com.tally.ui;

import com.tally.model.DailyLog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * GitHub-style year heatmap grid component.
 *
 * Layout (left to right = oldest to newest week):
 *
 *       Jan   Feb   Mar   Apr  ...  Dec
 *  M  [ ][ ][ ][ ][ ][ ][ ][ ]...[ ]
 *  T  [ ][ ][ ][ ][ ][ ][ ][ ]...[ ]
 *  W  [ ][ ][ ][ ][ ][ ][ ][ ]...[ ]
 *  T  [ ][ ][ ][ ][ ][ ][ ][ ]...[ ]
 *  F  [ ][ ][ ][ ][ ][ ][ ][ ]...[ ]
 *  S  [ ][ ][ ][ ][ ][ ][ ][ ]...[ ]
 *  S  [ ][ ][ ][ ][ ][ ][ ][ ]...[ ]
 *
 * Each cell = one day. Click to toggle completed/incomplete.
 * Future dates are dimmed and non-clickable.
 *
 * Built as a plain VBox (not an FXML file) because this is a reusable
 * programmatic component — the same grid is instantiated once per habit.
 */
public class HeatmapGrid extends VBox {

    // Cell dimensions — matches GitHub's contribution graph aesthetic
    private static final int CELL_SIZE = 12;
    private static final int CELL_GAP = 3;
    private static final int DAYS_IN_WEEK = 7;

    // The habit color (used for completed cells). Falls back to green.
    private final Color completedColor;
    private static final Color INCOMPLETE_COLOR = Color.web("#EEEEEE");
    private static final Color FUTURE_COLOR = Color.web("#F5F5F5");
    private static final Color TODAY_BORDER_COLOR = Color.web("#333333");

    private final int year;
    private final Long habitId;

    // Callback: called when user clicks a cell.
    // Arguments: (date, newCompletedState)
    private final BiConsumer<LocalDate, Boolean> onCellToggled;

    // Date → log, so we can look up state by date in O(1)
    private Map<LocalDate, DailyLog> logsByDate;

    // The inner grid we can rebuild when logs are updated
    private GridPane cellGrid;

    public HeatmapGrid(Long habitId, int year, String hexColor,
                       List<DailyLog> logs, BiConsumer<LocalDate, Boolean> onCellToggled) {
        this.habitId = habitId;
        this.year = year;
        this.onCellToggled = onCellToggled;
        this.completedColor = parseColor(hexColor);

        setSpacing(2);
        setPadding(new Insets(0, 8, 0, 0));

        buildGrid(logs);
    }

    /**
     * Rebuild the grid with updated log data.
     * Called after a cell is toggled to reflect the new state immediately.
     */
    public void updateLogs(List<DailyLog> logs) {
        buildGrid(logs);
    }

    // -------------------------------------------------------------------------
    // Grid construction
    // -------------------------------------------------------------------------

    private void buildGrid(List<DailyLog> logs) {
        getChildren().clear();

        logsByDate = logs.stream()
                .collect(Collectors.toMap(DailyLog::getLogDate, l -> l));

        // The grid starts on the Monday of the week containing Jan 1
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate gridStart = yearStart.with(DayOfWeek.MONDAY);
        if (gridStart.isAfter(yearStart)) {
            gridStart = gridStart.minusWeeks(1);
        }

        int totalWeeks = (int) Math.ceil(
                (double) (yearEnd.toEpochDay() - gridStart.toEpochDay() + 1) / 7);

        // Month labels row (top)
        HBox monthLabels = buildMonthLabels(gridStart, totalWeeks);

        // Main grid: day-of-week labels on left, cells on right
        HBox mainRow = new HBox(4);
        mainRow.setAlignment(Pos.TOP_LEFT);

        VBox dayLabels = buildDayLabels();
        cellGrid = buildCellGrid(gridStart, totalWeeks, yearStart, yearEnd);

        mainRow.getChildren().addAll(dayLabels, cellGrid);
        getChildren().addAll(monthLabels, mainRow);
    }

    private HBox buildMonthLabels(LocalDate gridStart, int totalWeeks) {
        HBox row = new HBox();
        row.setPadding(new Insets(0, 0, 2, 22)); // 22px left offset to align with cells

        int weekIndex = 0;
        LocalDate current = gridStart;
        Month lastLabeledMonth = null;

        // We build one label-or-spacer per week column
        while (weekIndex < totalWeeks) {
            // Check if this week starts a new month
            LocalDate weekMonday = gridStart.plusWeeks(weekIndex);
            Month month = weekMonday.getMonth();

            if (month != lastLabeledMonth) {
                Label lbl = new Label(month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                lbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #767676;");
                lbl.setMinWidth((CELL_SIZE + CELL_GAP));
                lbl.setPrefWidth((CELL_SIZE + CELL_GAP));
                row.getChildren().add(lbl);
                lastLabeledMonth = month;
            } else {
                // Spacer
                Label spacer = new Label();
                spacer.setMinWidth(CELL_SIZE + CELL_GAP);
                spacer.setPrefWidth(CELL_SIZE + CELL_GAP);
                row.getChildren().add(spacer);
            }
            weekIndex++;
        }

        return row;
    }

    private VBox buildDayLabels() {
        VBox labels = new VBox(CELL_GAP);
        labels.setAlignment(Pos.TOP_RIGHT);
        labels.setPadding(new Insets(0, 4, 0, 0));

        String[] dayNames = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < DAYS_IN_WEEK; i++) {
            Label lbl = new Label(dayNames[i]);
            lbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #767676;");
            lbl.setMinHeight(CELL_SIZE);
            lbl.setPrefHeight(CELL_SIZE);
            labels.getChildren().add(lbl);
        }
        return labels;
    }

    private GridPane buildCellGrid(LocalDate gridStart, int totalWeeks,
                                   LocalDate yearStart, LocalDate yearEnd) {
        GridPane grid = new GridPane();
        grid.setHgap(CELL_GAP);
        grid.setVgap(CELL_GAP);

        LocalDate today = LocalDate.now();

        for (int week = 0; week < totalWeeks; week++) {
            for (int dow = 0; dow < DAYS_IN_WEEK; dow++) {
                LocalDate date = gridStart.plusWeeks(week).plusDays(dow);

                // Skip days outside the year
                if (date.isBefore(yearStart) || date.isAfter(yearEnd)) {
                    Rectangle placeholder = new Rectangle(CELL_SIZE, CELL_SIZE);
                    placeholder.setFill(Color.TRANSPARENT);
                    grid.add(placeholder, week, dow);
                    continue;
                }

                Rectangle cell = buildCell(date, today);
                grid.add(cell, week, dow);
            }
        }

        return grid;
    }

    private Rectangle buildCell(LocalDate date, LocalDate today) {
        DailyLog log = logsByDate.get(date);
        boolean completed = log != null && log.isCompleted();
        boolean isFuture = date.isAfter(today);
        boolean isToday = date.equals(today);

        Rectangle rect = new Rectangle(CELL_SIZE, CELL_SIZE);
        rect.setArcWidth(3);
        rect.setArcHeight(3);

        if (isFuture) {
            rect.setFill(FUTURE_COLOR);
        } else if (completed) {
            rect.setFill(completedColor);
        } else {
            rect.setFill(INCOMPLETE_COLOR);
        }

        if (isToday) {
            rect.setStroke(TODAY_BORDER_COLOR);
            rect.setStrokeWidth(1.5);
        }

        // Tooltip: show date and completion status on hover
        String tooltipText = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + ", " + date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + date.getDayOfMonth() + ", " + date.getYear()
                + (completed ? " ✓" : isFuture ? "" : " ✗");
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(rect, tooltip);

        // Click handler — future dates are not clickable
        if (!isFuture) {
            rect.setStyle("-fx-cursor: hand;");
            rect.setOnMouseClicked(event -> {
                boolean newState = !completed;
                onCellToggled.accept(date, newState);

                // Optimistic UI update: flip color immediately before the API responds
                rect.setFill(newState ? completedColor : INCOMPLETE_COLOR);
            });
        }

        return rect;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Color parseColor(String hex) {
        if (hex == null || hex.isBlank()) return Color.web("#4CAF50");
        try {
            return Color.web(hex);
        } catch (Exception e) {
            return Color.web("#4CAF50");
        }
    }
}
