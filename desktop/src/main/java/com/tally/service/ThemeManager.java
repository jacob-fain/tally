package com.tally.service;

import javafx.scene.Scene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages application theme (light/dark mode).
 *
 * Persists theme preference to ~/.tally/theme.dat
 * Provides methods to apply and toggle themes on JavaFX scenes.
 */
public class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();

    private static final String LIGHT_THEME = "light";
    private static final String DARK_THEME = "dark";

    private String currentTheme;
    private final Path themeFilePath;

    private ThemeManager() {
        this.themeFilePath = getDefaultThemeFilePath();
        this.currentTheme = loadTheme();
    }

    /**
     * Package-private constructor for testing.
     * Allows injection of custom theme file path.
     */
    ThemeManager(Path themeFilePath) {
        this.themeFilePath = themeFilePath;
        this.currentTheme = loadTheme();
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Get current theme name.
     * @return "light" or "dark"
     */
    public String getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Check if current theme is dark mode.
     */
    public boolean isDarkMode() {
        return DARK_THEME.equals(currentTheme);
    }

    /**
     * Toggle between light and dark themes.
     * @param scene Scene to apply theme to
     */
    public void toggleTheme(Scene scene) {
        currentTheme = isDarkMode() ? LIGHT_THEME : DARK_THEME;
        applyTheme(scene);
        saveTheme();
    }

    /**
     * Apply current theme to a scene.
     * @param scene Scene to theme
     */
    public void applyTheme(Scene scene) {
        if (scene == null) return;

        // Switch the entire stylesheet
        scene.getStylesheets().clear();
        String cssFile = isDarkMode() ? "/com/tally/css/app-dark.css" : "/com/tally/css/app-light.css";
        String cssUrl = getClass().getResource(cssFile).toExternalForm();
        scene.getStylesheets().add(cssUrl);
    }

    /**
     * Get default theme file path, returns null if user.home is not available.
     */
    private static Path getDefaultThemeFilePath() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return null; // Can't persist theme (e.g., during tests)
        }
        return Paths.get(userHome, ".tally", "theme.dat");
    }

    /**
     * Load theme preference from disk.
     * @return theme name, defaults to "light"
     */
    private String loadTheme() {
        try {
            if (themeFilePath != null && Files.exists(themeFilePath)) {
                String theme = Files.readString(themeFilePath).trim();
                if (DARK_THEME.equals(theme)) {
                    return DARK_THEME;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load theme preference: " + e.getMessage());
        }
        return LIGHT_THEME; // Default to light theme
    }

    /**
     * Save theme preference to disk.
     */
    private void saveTheme() {
        try {
            if (themeFilePath == null) {
                return; // Can't persist (e.g., during tests)
            }

            Files.createDirectories(themeFilePath.getParent());
            Files.writeString(themeFilePath, currentTheme);

            // Set secure permissions (owner read/write only)
            try {
                Files.setPosixFilePermissions(themeFilePath,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                // Windows doesn't support POSIX permissions, skip
            }
        } catch (IOException e) {
            System.err.println("Failed to save theme preference: " + e.getMessage());
        }
    }

    /**
     * Set theme programmatically (for testing).
     * Package-private for test access.
     */
    void setTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return; // Invalid theme, keep current
        }

        String trimmed = theme.trim();
        if (DARK_THEME.equals(trimmed) || LIGHT_THEME.equals(trimmed)) {
            currentTheme = trimmed;
            saveTheme();
        }
        // Else: invalid theme, keep current
    }

    /**
     * Toggle theme without applying to a scene (for testing).
     * Package-private for test access.
     */
    void toggleTheme() {
        currentTheme = isDarkMode() ? LIGHT_THEME : DARK_THEME;
        saveTheme();
    }
}
