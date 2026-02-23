package com.tally.ui;

import com.tally.model.DailyLog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
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

    // Color scheme
    private static final Color COMPLETED_COLOR = Color.web("#4CAF50"); // Green for completed
    private static final Color INCOMPLETE_COLOR = Color.web("#F44336"); // Red for incomplete (past days)
    private static final Color FUTURE_COLOR = Color.web("#9E9E9E"); // Grey for future days
    private static final Color TODAY_BORDER_COLOR = Color.web("#333333");

    // Keep this for backwards compatibility but we'll use the static colors above
    private final Color completedColor;

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
                .collect(Collectors.toMap(DailyLog::getLogDate, l -> l, (existing, replacement) -> replacement));

        // The grid starts on the Monday of the week containing Jan 1
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate gridStart = yearStart.with(DayOfWeek.MONDAY);
        if (gridStart.isAfter(yearStart)) {
            gridStart = gridStart.minusWeeks(1);
        }

        int totalWeeks = (int) Math.ceil(
                (double) (yearEnd.toEpochDay() - gridStart.toEpochDay() + 1) / 7);

        // Month labels row (top) with dividers
        HBox monthLabelsRow = buildMonthLabelsWithDividers(gridStart, totalWeeks, yearStart, yearEnd);

        // Main grid: day-of-week labels on left, cells on right
        HBox mainRow = new HBox(4);
        mainRow.setAlignment(Pos.TOP_LEFT);

        VBox dayLabels = buildDayLabels();

        // Create a container for cells + divider lines overlaid
        StackPane cellContainer = new StackPane();
        cellContainer.setAlignment(Pos.TOP_LEFT);

        cellGrid = buildCellGrid(gridStart, totalWeeks, yearStart, yearEnd);
        Pane dividerLines = buildMonthDividerLines(gridStart, totalWeeks, yearStart, yearEnd);

        cellContainer.getChildren().addAll(cellGrid, dividerLines);

        mainRow.getChildren().addAll(dayLabels, cellContainer);
        getChildren().addAll(monthLabelsRow, mainRow);
    }

    private HBox buildMonthLabelsWithDividers(LocalDate gridStart, int totalWeeks,
                                              LocalDate yearStart, LocalDate yearEnd) {
        HBox row = new HBox();
        row.setPadding(new Insets(0, 0, 4, 22)); // 22px left offset to align with cells
        row.setAlignment(Pos.BOTTOM_LEFT);

        // Calculate which weeks belong to each month
        Month currentMonth = null;
        int monthStartWeek = 0;
        int weekIndex = 0;

        while (weekIndex < totalWeeks) {
            LocalDate weekStart = gridStart.plusWeeks(weekIndex);
            // Find the first day in this week that's in the year
            LocalDate firstDayInWeek = weekStart;
            for (int d = 0; d < 7; d++) {
                LocalDate day = weekStart.plusDays(d);
                if (!day.isBefore(yearStart) && !day.isAfter(yearEnd)) {
                    firstDayInWeek = day;
                    break;
                }
            }

            Month month = firstDayInWeek.getMonth();

            // If month changed, create a label for the previous month
            if (currentMonth != null && month != currentMonth) {
                int monthWeeks = weekIndex - monthStartWeek;
                double labelWidth = monthWeeks * (CELL_SIZE + CELL_GAP);
                Label monthLabel = new Label(currentMonth.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                monthLabel.getStyleClass().add("heatmap-label");
                monthLabel.setMinWidth(labelWidth);
                monthLabel.setPrefWidth(labelWidth);
                monthLabel.setMaxWidth(labelWidth);
                monthLabel.setAlignment(Pos.CENTER);
                row.getChildren().add(monthLabel);

                monthStartWeek = weekIndex;
            }

            if (currentMonth == null) {
                currentMonth = month;
            }
            currentMonth = month;
            weekIndex++;
        }

        // Add the last month label
        if (currentMonth != null) {
            int monthWeeks = totalWeeks - monthStartWeek;
            double labelWidth = monthWeeks * (CELL_SIZE + CELL_GAP);
            Label monthLabel = new Label(currentMonth.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            monthLabel.getStyleClass().add("heatmap-label");
            monthLabel.setMinWidth(labelWidth);
            monthLabel.setPrefWidth(labelWidth);
            monthLabel.setMaxWidth(labelWidth);
            monthLabel.setAlignment(Pos.CENTER);
            row.getChildren().add(monthLabel);
        }

        return row;
    }

    private Pane buildMonthDividerLines(LocalDate gridStart, int totalWeeks,
                                        LocalDate yearStart, LocalDate yearEnd) {
        Pane pane = new Pane();
        pane.setMouseTransparent(true); // Don't interfere with cell clicks

        Month currentMonth = null;
        LocalDate today = LocalDate.now();

        for (int week = 0; week < totalWeeks; week++) {
            LocalDate weekStart = gridStart.plusWeeks(week);

            // Find the first valid day in this week
            Month weekMonth = null;
            for (int d = 0; d < 7; d++) {
                LocalDate day = weekStart.plusDays(d);
                if (!day.isBefore(yearStart) && !day.isAfter(yearEnd)) {
                    weekMonth = day.getMonth();
                    break;
                }
            }

            if (weekMonth == null) continue;

            // If month changed, draw a divider line
            if (currentMonth != null && weekMonth != currentMonth && week > 0) {
                // Calculate the height of the divider (only through days in the previous month)
                double lineHeight = 0;
                for (int d = 0; d < 7; d++) {
                    LocalDate day = gridStart.plusWeeks(week - 1).plusDays(d);
                    if (!day.isBefore(yearStart) && !day.isAfter(yearEnd) &&
                        day.getMonth() == currentMonth) {
                        lineHeight = (d + 1) * (CELL_SIZE + CELL_GAP);
                    }
                }

                double xPos = week * (CELL_SIZE + CELL_GAP) - CELL_GAP / 2.0;
                Line line = new Line(xPos, 0, xPos, lineHeight);
                line.setStroke(Color.web("#CCCCCC"));
                line.setStrokeWidth(1);
                pane.getChildren().add(line);
            }

            currentMonth = weekMonth;
        }

        return pane;
    }

    private VBox buildDayLabels() {
        VBox labels = new VBox(CELL_GAP);
        labels.setAlignment(Pos.TOP_RIGHT);
        labels.setPadding(new Insets(0, 4, 0, 0));

        String[] dayNames = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < DAYS_IN_WEEK; i++) {
            Label lbl = new Label(dayNames[i]);
            lbl.getStyleClass().add("heatmap-label");
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

        Color fillColor;
        if (isFuture) {
            fillColor = FUTURE_COLOR; // Grey for future
        } else if (completed) {
            fillColor = COMPLETED_COLOR; // Green for completed
        } else {
            fillColor = INCOMPLETE_COLOR; // Red for incomplete past days
        }
        rect.setFill(fillColor);

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
            rect.setCursor(javafx.scene.Cursor.HAND);

            // Add hover effect to make it clear the cell is clickable
            rect.setOnMouseEntered(event -> {
                rect.setOpacity(0.7);
            });
            rect.setOnMouseExited(event -> {
                rect.setOpacity(1.0);
            });

            rect.setOnMouseClicked(event -> {
                // Always check current fill color to determine new state (not the captured 'completed' variable)
                boolean currentlyCompleted = rect.getFill().equals(COMPLETED_COLOR);
                boolean newState = !currentlyCompleted;
                onCellToggled.accept(date, newState);

                // Optimistic UI update: flip color immediately before the API responds
                rect.setFill(newState ? COMPLETED_COLOR : INCOMPLETE_COLOR);
            });
        } else {
            rect.setCursor(javafx.scene.Cursor.DEFAULT);
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
