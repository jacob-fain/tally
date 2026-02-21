package com.tally.controller;

import com.tally.TallyApp;
import com.tally.model.DailyLog;
import com.tally.model.Habit;
import com.tally.service.AuthService;
import com.tally.service.ExportService;
import com.tally.service.HabitService;
import com.tally.service.LogService;
import com.tally.service.ThemeManager;
import com.tally.ui.HabitRow;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Controller for the main window (main.fxml).
 *
 * Responsibilities:
 * - Load all habits from the API on startup
 * - For each habit, fetch the year's logs and create a HabitRow
 * - Handle year navigation (prev/next)
 * - Handle adding new habits
 * - Handle logout
 */
public class MainController {

    @FXML private Label usernameLabel;
    @FXML private Label yearLabel;
    @FXML private Button prevYearBtn;
    @FXML private Button nextYearBtn;
    @FXML private VBox habitsContainer;
    @FXML private VBox loadingPane;
    @FXML private VBox emptyPane;
    @FXML private TextField newHabitNameField;
    @FXML private Button addHabitBtn;
    @FXML private Button themeToggleBtn;

    private final AuthService authService = AuthService.getInstance();
    private final HabitService habitService = HabitService.getInstance();
    private final LogService logService = LogService.getInstance();
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private final ExportService exportService = ExportService.getInstance();

    private static final int MIN_YEAR = 2020; // Reasonable minimum - adjust if needed
    private int currentYear = LocalDate.now().getYear();
    private List<Habit> habits = new ArrayList<>();

    @FXML
    private void initialize() {
        System.out.println("MainController.initialize() called");
        if (authService.getUsername() != null) {
            usernameLabel.setText(authService.getUsername());
        }
        yearLabel.setText(String.valueOf(currentYear));
        // Can't navigate to future years
        nextYearBtn.setDisable(currentYear >= LocalDate.now().getYear());

        // Allow pressing Enter in the new habit field to add a habit
        newHabitNameField.setOnAction(event -> onAddHabitClicked());

        // Set up tooltips with keyboard shortcuts
        setupTooltips();

        // Apply current theme and update button text
        themeManager.applyTheme(yearLabel.getScene());
        updateThemeToggleButton();

        // Set up keyboard shortcuts
        setupKeyboardShortcuts();

        System.out.println("About to call loadHabits()");
        loadHabits();
        System.out.println("loadHabits() returned");
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    /**
     * Load all habits, then for each habit load its year of logs.
     * Runs on a background thread — updates UI on JavaFX Application Thread.
     */
    private void loadHabits() {
        showLoading(true);

        Task<List<Habit>> task = new Task<>() {
            @Override
            protected List<Habit> call() throws Exception {
                return habitService.getHabits();
            }
        };

        task.setOnSucceeded(event -> {
            habits = task.getValue();
            if (habits.isEmpty()) {
                showLoading(false);
                showEmpty(true);
            } else {
                loadLogsAndRender(habits);
            }
        });

        task.setOnFailed(event -> {
            showLoading(false);
            System.err.println("Failed to load habits: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Fetch logs for all habits in parallel (one Task per habit) and render
     * each HabitRow as soon as its logs arrive.
     */
    private void loadLogsAndRender(List<Habit> habits) {
        showLoading(false);
        showEmpty(false);

        // Clear existing habit rows (keep loading/empty panes)
        habitsContainer.getChildren().removeIf(n -> n instanceof HabitRow);

        LocalDate yearStart = LocalDate.of(currentYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(currentYear, 12, 31);

        for (Habit habit : habits) {
            Task<List<DailyLog>> logTask = new Task<>() {
                @Override
                protected List<DailyLog> call() throws Exception {
                    return logService.getLogsForHabit(habit.getId(), yearStart, yearEnd);
                }
            };

            logTask.setOnSucceeded(event -> {
                List<DailyLog> logs = logTask.getValue();
                HabitRow row = new HabitRow(habit, currentYear, logs,
                        () -> onDeleteHabit(habit));
                // Add in display order
                Platform.runLater(() -> habitsContainer.getChildren().add(row));
            });

            logTask.setOnFailed(event -> {
                // Render row with empty logs so the grid still shows
                HabitRow row = new HabitRow(habit, currentYear, List.of(),
                        () -> onDeleteHabit(habit));
                Platform.runLater(() -> habitsContainer.getChildren().add(row));
                System.err.println("Failed to load logs for habit " + habit.getId()
                        + ": " + logTask.getException().getMessage());
            });

            Thread thread = new Thread(logTask);
            thread.setDaemon(true);
            thread.start();
        }
    }

    // -------------------------------------------------------------------------
    // Year navigation
    // -------------------------------------------------------------------------

    @FXML
    private void onPrevYear() {
        if (currentYear > MIN_YEAR) {
            currentYear--;
            yearLabel.setText(String.valueOf(currentYear));
            nextYearBtn.setDisable(false);
            prevYearBtn.setDisable(currentYear <= MIN_YEAR);
            loadLogsAndRender(habits);
        }
    }

    @FXML
    private void onNextYear() {
        if (currentYear < LocalDate.now().getYear()) {
            currentYear++;
            yearLabel.setText(String.valueOf(currentYear));
            nextYearBtn.setDisable(currentYear >= LocalDate.now().getYear());
            loadLogsAndRender(habits);
        }
    }

    // -------------------------------------------------------------------------
    // Add habit
    // -------------------------------------------------------------------------

    @FXML
    private void onAddHabitClicked() {
        String name = newHabitNameField.getText().trim();
        if (name.isBlank()) {
            newHabitNameField.requestFocus();
            return;
        }

        addHabitBtn.setDisable(true);
        newHabitNameField.setDisable(true);

        Task<Habit> task = new Task<>() {
            @Override
            protected Habit call() throws Exception {
                return habitService.createHabit(name, "#4CAF50");
            }
        };

        task.setOnSucceeded(event -> {
            Habit newHabit = task.getValue();
            habits.add(newHabit);

            HabitRow row = new HabitRow(newHabit, currentYear, List.of(),
                    () -> onDeleteHabit(newHabit));
            habitsContainer.getChildren().add(row);

            newHabitNameField.clear();
            newHabitNameField.setDisable(false);
            addHabitBtn.setDisable(false);
            showEmpty(false);
        });

        task.setOnFailed(event -> {
            newHabitNameField.setDisable(false);
            addHabitBtn.setDisable(false);
            System.err.println("Failed to create habit: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // -------------------------------------------------------------------------
    // Delete habit
    // -------------------------------------------------------------------------

    private void onDeleteHabit(Habit habit) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                habitService.deleteHabit(habit.getId());
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            habits.remove(habit);
            // Remove the HabitRow from the container
            habitsContainer.getChildren().removeIf(n ->
                    n instanceof HabitRow row && row.getHabitId().equals(habit.getId()));
            if (habits.isEmpty()) showEmpty(true);
        });

        task.setOnFailed(event ->
                System.err.println("Failed to delete habit: " + task.getException().getMessage()));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // -------------------------------------------------------------------------
    // Keyboard shortcuts
    // -------------------------------------------------------------------------

    /**
     * Add tooltips to buttons showing keyboard shortcuts.
     */
    private void setupTooltips() {
        String modifierKey = System.getProperty("os.name").toLowerCase().contains("mac") ? "Cmd" : "Ctrl";

        Tooltip.install(prevYearBtn, new Tooltip("Previous Year (Alt+←)"));
        Tooltip.install(nextYearBtn, new Tooltip("Next Year (Alt+→)"));
        Tooltip.install(themeToggleBtn, new Tooltip("Toggle Theme (" + modifierKey + "+T)"));
        Tooltip.install(addHabitBtn, new Tooltip("Add Habit (" + modifierKey + "+N to focus)"));

        Tooltip exportTooltip = new Tooltip("Export Data (" + modifierKey + "+E)");
        // Find export button by searching for it in the scene
        Platform.runLater(() -> {
            yearLabel.getScene().getRoot().lookupAll(".toolbar-button").forEach(node -> {
                if (node instanceof Button btn && "Export".equals(btn.getText())) {
                    Tooltip.install(btn, exportTooltip);
                }
            });
        });

        newHabitNameField.setTooltip(new Tooltip(modifierKey + "+N to focus, Esc to clear"));
    }

    /**
     * Set up global keyboard shortcuts for the main window.
     *
     * Shortcuts:
     * - Alt+Left/Right: Navigate years
     * - Ctrl/Cmd+E: Export data
     * - Ctrl/Cmd+T: Toggle theme
     * - Ctrl/Cmd+N: Focus new habit field
     * - Escape: Clear new habit field
     */
    private void setupKeyboardShortcuts() {
        yearLabel.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(this::handleKeyPress);
            }
        });
    }

    private void handleKeyPress(KeyEvent event) {
        boolean isCtrlOrCmd = event.isShortcutDown(); // Ctrl on Windows/Linux, Cmd on Mac

        // Alt+Left: Previous year
        if (event.isAltDown() && event.getCode() == KeyCode.LEFT) {
            if (!prevYearBtn.isDisabled()) {
                onPrevYear();
            }
            event.consume();
            return;
        }

        // Alt+Right: Next year
        if (event.isAltDown() && event.getCode() == KeyCode.RIGHT) {
            if (!nextYearBtn.isDisabled()) {
                onNextYear();
            }
            event.consume();
            return;
        }

        // Ctrl/Cmd+E: Export
        if (isCtrlOrCmd && event.getCode() == KeyCode.E) {
            onExportClicked();
            event.consume();
            return;
        }

        // Ctrl/Cmd+T: Toggle theme
        if (isCtrlOrCmd && event.getCode() == KeyCode.T) {
            onThemeToggleClicked();
            event.consume();
            return;
        }

        // Ctrl/Cmd+N: Focus new habit field
        if (isCtrlOrCmd && event.getCode() == KeyCode.N) {
            newHabitNameField.requestFocus();
            event.consume();
            return;
        }

        // Escape: Clear new habit field (if focused)
        if (event.getCode() == KeyCode.ESCAPE && newHabitNameField.isFocused()) {
            newHabitNameField.clear();
            event.consume();
        }
    }

    // -------------------------------------------------------------------------
    // Export data
    // -------------------------------------------------------------------------

    @FXML
    private void onExportClicked() {
        // Show format choice dialog
        ChoiceDialog<String> formatDialog = new ChoiceDialog<>("CSV", "CSV", "JSON");
        formatDialog.setTitle("Export Data");
        formatDialog.setHeaderText("Choose export format");
        formatDialog.setContentText("Format:");

        Optional<String> formatChoice = formatDialog.showAndWait();
        if (formatChoice.isEmpty()) {
            return; // User cancelled
        }

        String format = formatChoice.get();
        boolean isCsv = "CSV".equals(format);

        // Show file chooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Habit Data");
        fileChooser.setInitialFileName("tally-export-" + LocalDate.now() + (isCsv ? ".csv" : ".json"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        format + " Files",
                        isCsv ? "*.csv" : "*.json"
                )
        );

        File file = fileChooser.showSaveDialog(TallyApp.getPrimaryStage());
        if (file == null) {
            return; // User cancelled
        }

        // Export in background
        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Fetch all logs for all habits (all time, not just current year)
                Map<Long, List<DailyLog>> logsMap = new HashMap<>();
                for (Habit habit : habits) {
                    // Fetch logs from 2020 to current year
                    LocalDate start = LocalDate.of(MIN_YEAR, 1, 1);
                    LocalDate end = LocalDate.of(LocalDate.now().getYear(), 12, 31);
                    List<DailyLog> logs = logService.getLogsForHabit(habit.getId(), start, end);
                    logsMap.put(habit.getId(), logs);
                }

                // Export to file
                Path filePath = file.toPath();
                if (isCsv) {
                    exportService.exportToCsv(habits, logsMap, filePath);
                } else {
                    exportService.exportToJson(habits, logsMap, filePath);
                }

                return null;
            }
        };

        exportTask.setOnSucceeded(event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Successful");
            alert.setHeaderText(null);
            alert.setContentText("Data exported to:\n" + file.getAbsolutePath());
            alert.showAndWait();
        });

        exportTask.setOnFailed(event -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Export Failed");
            alert.setHeaderText("Failed to export data");
            alert.setContentText(exportTask.getException().getMessage());
            alert.showAndWait();
        });

        Thread thread = new Thread(exportTask);
        thread.setDaemon(true);
        thread.start();
    }

    // -------------------------------------------------------------------------
    // Theme toggle
    // -------------------------------------------------------------------------

    @FXML
    private void onThemeToggleClicked() {
        themeManager.toggleTheme(themeToggleBtn.getScene());
        updateThemeToggleButton();
    }

    private void updateThemeToggleButton() {
        if (themeToggleBtn != null) {
            themeToggleBtn.setText(themeManager.isDarkMode() ? "☀" : "🌙");
        }
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    @FXML
    private void onLogoutClicked() {
        authService.logout();
        try {
            TallyApp.showLoginScreen();
        } catch (IOException e) {
            System.err.println("Failed to navigate to login: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // UI state helpers
    // -------------------------------------------------------------------------

    private void showLoading(boolean show) {
        loadingPane.setVisible(show);
        loadingPane.setManaged(show);
    }

    private void showEmpty(boolean show) {
        emptyPane.setVisible(show);
        emptyPane.setManaged(show);
    }
}
