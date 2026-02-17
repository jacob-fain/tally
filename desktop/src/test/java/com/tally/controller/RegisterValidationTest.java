package com.tally.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RegisterController input validation logic.
 *
 * Problem: RegisterController has @FXML fields that require the JavaFX runtime.
 * Running JavaFX in a headless test environment requires special setup (TestFX
 * or a virtual display), which we'll add in a future phase.
 *
 * Workaround: Extract the validation logic into a testable private method and
 * test it via reflection. This keeps the validation tested without needing
 * the full JavaFX runtime.
 *
 * Alternative for future: Use TestFX library for full UI tests.
 * TestFX can run headless with -Djava.awt.headless=true and the monocle graphics system.
 */
class RegisterValidationTest {

    private RegisterController controller;
    private Method validateMethod;

    @BeforeEach
    void setUp() throws Exception {
        controller = new RegisterController();
        validateMethod = RegisterController.class.getDeclaredMethod(
                "validate", String.class, String.class, String.class, String.class);
        validateMethod.setAccessible(true);
    }

    private String validate(String username, String email, String password, String confirm)
            throws Exception {
        return (String) validateMethod.invoke(controller, username, email, password, confirm);
    }

    // =========================================================================
    // Username validation
    // =========================================================================

    @Test
    void shouldRejectBlankUsername() throws Exception {
        String error = validate("", "test@test.com", "password123", "password123");
        assertNotNull(error);
        assertTrue(error.toLowerCase().contains("username"));
    }

    @Test
    void shouldRejectUsernameTooShort() throws Exception {
        String error = validate("ab", "test@test.com", "password123", "password123");
        assertNotNull(error);
        assertTrue(error.contains("3"));
    }

    @Test
    void shouldRejectUsernameTooLong() throws Exception {
        String longUsername = "a".repeat(51);
        String error = validate(longUsername, "test@test.com", "password123", "password123");
        assertNotNull(error);
        assertTrue(error.contains("50"));
    }

    @Test
    void shouldRejectUsernameWithSpecialChars() throws Exception {
        String error = validate("user name!", "test@test.com", "password123", "password123");
        assertNotNull(error);
        assertTrue(error.toLowerCase().contains("letters"));
    }

    @Test
    void shouldAcceptValidUsername() throws Exception {
        assertNull(validate("jacob_123", "test@test.com", "password123", "password123"));
    }

    // =========================================================================
    // Email validation
    // =========================================================================

    @Test
    void shouldRejectBlankEmail() throws Exception {
        String error = validate("jacob", "", "password123", "password123");
        assertNotNull(error);
        assertTrue(error.toLowerCase().contains("email"));
    }

    @Test
    void shouldRejectEmailWithoutAtSign() throws Exception {
        String error = validate("jacob", "notanemail.com", "password123", "password123");
        assertNotNull(error);
    }

    @Test
    void shouldAcceptValidEmail() throws Exception {
        assertNull(validate("jacob", "jacob@example.com", "password123", "password123"));
    }

    // =========================================================================
    // Password validation
    // =========================================================================

    @Test
    void shouldRejectShortPassword() throws Exception {
        String error = validate("jacob", "jacob@test.com", "short", "short");
        assertNotNull(error);
        assertTrue(error.contains("8"));
    }

    @Test
    void shouldRejectMismatchedPasswords() throws Exception {
        String error = validate("jacob", "jacob@test.com", "password123", "different123");
        assertNotNull(error);
        assertTrue(error.toLowerCase().contains("match"));
    }

    @Test
    void shouldAcceptMatchingValidPasswords() throws Exception {
        assertNull(validate("jacob", "jacob@test.com", "password123", "password123"));
    }

    // =========================================================================
    // All valid — no error
    // =========================================================================

    @Test
    void shouldReturnNullForCompletelyValidInput() throws Exception {
        assertNull(validate("jacob_fain", "jacob@example.com", "securePass99", "securePass99"));
    }
}
