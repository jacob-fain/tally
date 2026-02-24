package com.tally.service;

import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ThemeManager theme persistence and validation.
 *
 * Note: Some tests involving file permissions are Unix-specific and may
 * not pass on Windows. These tests verify defense-in-depth measures.
 */
class ThemeManagerTest {

    @TempDir
    Path tempDir;

    private Path themeFilePath;

    @BeforeEach
    void setUp() {
        // Use temp directory for theme file to avoid polluting user's home directory
        themeFilePath = tempDir.resolve("theme.dat");
    }

    // =========================================================================
    // Theme Persistence Tests
    // =========================================================================

    @Test
    void shouldSaveThemeToFile() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // When
        themeManager.setTheme("dark");

        // Then
        assertTrue(Files.exists(themeFilePath));
        String content = Files.readString(themeFilePath);
        assertEquals("dark", content.trim());
    }

    @Test
    void shouldLoadSavedTheme() throws IOException {
        // Given
        Files.writeString(themeFilePath, "dark");

        // When
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // Then
        assertEquals("dark", themeManager.getCurrentTheme());
    }

    @Test
    void shouldDefaultToLightThemeWhenFileDoesNotExist() {
        // Given: No theme file exists

        // When
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // Then
        assertEquals("light", themeManager.getCurrentTheme());
    }

    @Test
    void shouldDefaultToLightThemeWhenFileIsEmpty() throws IOException {
        // Given
        Files.writeString(themeFilePath, "");

        // When
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // Then
        assertEquals("light", themeManager.getCurrentTheme());
    }

    @Test
    void shouldDefaultToLightThemeWhenFileContainsWhitespace() throws IOException {
        // Given
        Files.writeString(themeFilePath, "   \n\t  ");

        // When
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // Then
        assertEquals("light", themeManager.getCurrentTheme());
    }

    // =========================================================================
    // Theme Validation Tests
    // =========================================================================

    @Test
    void shouldAcceptValidDarkTheme() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // When
        themeManager.setTheme("dark");

        // Then
        assertEquals("dark", themeManager.getCurrentTheme());
        assertEquals("dark", Files.readString(themeFilePath).trim());
    }

    @Test
    void shouldAcceptValidLightTheme() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // When
        themeManager.setTheme("light");

        // Then
        assertEquals("light", themeManager.getCurrentTheme());
        assertEquals("light", Files.readString(themeFilePath).trim());
    }

    @Test
    void shouldRejectInvalidTheme() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // When
        themeManager.setTheme("hacker"); // Invalid theme

        // Then
        // Should fall back to light (default)
        assertEquals("light", themeManager.getCurrentTheme());
        assertFalse(Files.exists(themeFilePath), "Should not save invalid theme");
    }

    @Test
    void shouldRejectNullTheme() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);
        themeManager.setTheme("dark"); // Set valid theme first

        // When
        themeManager.setTheme(null);

        // Then
        // Should keep previous valid theme
        assertEquals("dark", themeManager.getCurrentTheme());
    }

    @Test
    void shouldRejectEmptyTheme() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);
        themeManager.setTheme("dark"); // Set valid theme first

        // When
        themeManager.setTheme("");

        // Then
        // Should keep previous valid theme
        assertEquals("dark", themeManager.getCurrentTheme());
    }

    @Test
    void shouldHandleThemeWithWhitespace() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // When
        themeManager.setTheme("  dark  ");

        // Then
        // Should trim and accept
        assertEquals("dark", themeManager.getCurrentTheme());
        assertEquals("dark", Files.readString(themeFilePath).trim());
    }

    @Test
    void shouldRejectThemeWithInjectionAttempt() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // When
        themeManager.setTheme("dark\n../../../etc/passwd");

        // Then
        // Should reject and fall back to light
        assertEquals("light", themeManager.getCurrentTheme());
        assertFalse(Files.exists(themeFilePath));
    }

    // =========================================================================
    // File Permission Tests (Unix-specific)
    // =========================================================================

    @Test
    void shouldSetRestrictiveFilePermissionsOnUnix() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // When
        themeManager.setTheme("dark");

        // Then
        if (isUnix()) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(themeFilePath);
            assertTrue(permissions.contains(PosixFilePermission.OWNER_READ));
            assertTrue(permissions.contains(PosixFilePermission.OWNER_WRITE));
            assertFalse(permissions.contains(PosixFilePermission.GROUP_READ));
            assertFalse(permissions.contains(PosixFilePermission.OTHERS_READ));
        }
    }

    @Test
    void shouldCreateParentDirectoryIfNotExists() throws IOException {
        // Given
        Path nestedPath = tempDir.resolve("nested/subdir/theme.dat");
        ThemeManager themeManager = new ThemeManager(nestedPath);

        // When
        themeManager.setTheme("dark");

        // Then
        assertTrue(Files.exists(nestedPath));
        assertEquals("dark", Files.readString(nestedPath).trim());
    }

    // =========================================================================
    // Theme Toggle Tests
    // =========================================================================

    @Test
    void shouldToggleFromLightToDark() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);
        themeManager.setTheme("light");

        // When
        themeManager.toggleTheme();

        // Then
        assertEquals("dark", themeManager.getCurrentTheme());
    }

    @Test
    void shouldToggleFromDarkToLight() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);
        themeManager.setTheme("dark");

        // When
        themeManager.toggleTheme();

        // Then
        assertEquals("light", themeManager.getCurrentTheme());
    }

    @Test
    void shouldPersistToggledTheme() throws IOException {
        // Given
        ThemeManager themeManager = new ThemeManager(themeFilePath);
        themeManager.setTheme("light");

        // When
        themeManager.toggleTheme();

        // Then
        assertEquals("dark", Files.readString(themeFilePath).trim());
    }

    // =========================================================================
    // Theme Loading from Corrupted File Tests
    // =========================================================================

    @Test
    void shouldHandleCorruptedThemeFile() throws IOException {
        // Given: File with invalid content
        Files.writeString(themeFilePath, "some-invalid-theme-123");

        // When
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // Then
        // Should fall back to default (light)
        assertEquals("light", themeManager.getCurrentTheme());
    }

    @Test
    void shouldHandleThemeFileWithMultipleLines() throws IOException {
        // Given: File with multiple lines (invalid content)
        Files.writeString(themeFilePath, "dark\nlight\nhacker");

        // When
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // Then
        // Should fall back to default since content doesn't match "dark" or "light" exactly
        assertEquals("light", themeManager.getCurrentTheme());
    }

    @Test
    void shouldHandleThemeFileWithCaseMismatch() throws IOException {
        // Given
        Files.writeString(themeFilePath, "DARK");

        // When
        ThemeManager themeManager = new ThemeManager(themeFilePath);

        // Then
        // Should be case-sensitive and reject
        assertEquals("light", themeManager.getCurrentTheme());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private boolean isUnix() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux") || os.contains("mac");
    }
}
