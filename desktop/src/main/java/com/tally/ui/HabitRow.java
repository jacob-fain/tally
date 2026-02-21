package com.tally.ui;

import com.tally.model.DailyLog;
import com.tally.model.Habit;
import com.tally.service.LogService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A single habit row in the main view:
 *
 *  [Habit Name]  [streak: 5 days | 73%]  [Delete]
 *  [GitHub-style heatmap grid]
 *
 * This is a self-contained component — it owns its HeatmapGrid and handles
 * cell-toggle API calls internally. The MainController just creates one
 * HabitRow per habit and stacks them in a VBox.
 */
public class HabitRow extends VBox {

    private final Habit habit;
    private final int year;
    private List<DailyLog> logs;

    private final LogService logService;
    private HeatmapGrid heatmapGrid;
    private Label statsLabel;

    // Callback to parent when this habit should be deleted from the list
    private final Runnable onDeleteRequested;

    public Long getHabitId() { return habit.getId(); }

    public HabitRow(Habit habit, int year, List<DailyLog> logs, Runnable onDeleteRequested) {
        this.habit = habit;
        this.year = year;
        this.logs = new ArrayList<>(logs);
        this.logService = LogService.getInstance();
        this.onDeleteRequested = onDeleteRequested;

        setSpacing(6);
        setPadding(new Insets(12, 16, 12, 16));
        setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 6px;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 1);");

        buildRow();
    }

    private void buildRow() {
        // --- Header row: habit name + stats + delete button ---
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(habit.getName());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1A1A1A;");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        statsLabel = new Label();
        statsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575;");
        updateStats();

        Button deleteBtn = new Button("×");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #BDBDBD;"
                + "-fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 0 4;");
        deleteBtn.setOnMouseEntered(e -> deleteBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #E53935;"
                        + "-fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 0 4;"));
        deleteBtn.setOnMouseExited(e -> deleteBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #BDBDBD;"
                        + "-fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 0 4;"));
        deleteBtn.setOnAction(e -> onDeleteRequested.run());

        header.getChildren().addAll(nameLabel, statsLabel, deleteBtn);

        // --- Heatmap grid ---
        heatmapGrid = new HeatmapGrid(habit.getId(), year, habit.getColor(), logs, this::onCellToggled);

        getChildren().setAll(header, heatmapGrid);
    }

    // -------------------------------------------------------------------------
    // Cell toggle
    // -------------------------------------------------------------------------

    /**
     * Called when the user clicks a cell in the heatmap.
     * Runs the API call on a background thread; updates stats on success.
     */
    private void onCellToggled(LocalDate date, boolean newState) {
        Task<DailyLog> task = new Task<>() {
            @Override
            protected DailyLog call() throws Exception {
                return logService.setLog(habit.getId(), date, newState);
            }
        };

        task.setOnSucceeded(event -> {
            DailyLog updated = task.getValue();
            // Update our local log list to reflect the change
            logs.removeIf(l -> l.getLogDate().equals(date));
            logs.add(updated);
            // Refresh stats label (grid already updated optimistically in HeatmapGrid)
            Platform.runLater(this::updateStats);
        });

        task.setOnFailed(event -> {
            // Revert the optimistic update by rebuilding the grid with original data
            Platform.runLater(() -> heatmapGrid.updateLogs(logs));
            System.err.println("Failed to toggle log: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // -------------------------------------------------------------------------
    // Stats calculation
    // -------------------------------------------------------------------------

    private void updateStats() {
        int streak = calculateStreak();
        double pct = calculateCompletionPct();
        statsLabel.setText(String.format("%d day streak  ·  %.0f%%", streak, pct));
    }

    /**
     * Current streak: count consecutive completed days ending at today (backwards).
     */
    private int calculateStreak() {
        LocalDate date = LocalDate.now();
        int streak = 0;
        while (true) {
            final LocalDate d = date;
            boolean completed = logs.stream()
                    .anyMatch(l -> l.getLogDate().equals(d) && l.isCompleted());
            if (!completed) break;
            streak++;
            date = date.minusDays(1);
        }
        return streak;
    }

    /**
     * Completion % for the current year up to today.
     */
    private double calculateCompletionPct() {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate today = LocalDate.now();
        long daysSoFar = today.toEpochDay() - yearStart.toEpochDay() + 1;
        if (daysSoFar <= 0) return 0;

        long completed = logs.stream()
                .filter(DailyLog::isCompleted)
                .filter(l -> !l.getLogDate().isAfter(today))
                .count();

        return (completed * 100.0) / daysSoFar;
    }
}
