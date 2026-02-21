package com.tally.controller;

import com.tally.TallyApp;
import com.tally.service.AuthService;
import com.tally.service.ThemeManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;

/**
 * Controller for the register screen (register.fxml).
 * Follows the same threading pattern as LoginController.
 */
public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerButton;
    @FXML private Label errorLabel;

    private final AuthService authService = AuthService.getInstance();
    private final ThemeManager themeManager = ThemeManager.getInstance();

    @FXML
    private void initialize() {
        confirmPasswordField.setOnAction(event -> onRegisterClicked());

        // Apply saved theme
        themeManager.applyTheme(usernameField.getScene());
    }

    @FXML
    private void onRegisterClicked() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        // Client-side validation
        String validationError = validate(username, email, password, confirm);
        if (validationError != null) {
            showError(validationError);
            return;
        }

        hideError();
        setLoading(true);

        Task<AuthService.AuthResult> registerTask = new Task<>() {
            @Override
            protected AuthService.AuthResult call() throws Exception {
                return authService.register(username, email, password);
            }
        };

        registerTask.setOnSucceeded(event -> {
            setLoading(false);
            AuthService.AuthResult result = registerTask.getValue();
            if (result.isSuccess()) {
                try {
                    TallyApp.showMainWindow();
                } catch (IOException e) {
                    showError("Failed to load main window: " + e.getMessage());
                }
            } else {
                showError(result.getErrorMessage());
            }
        });

        registerTask.setOnFailed(event -> {
            setLoading(false);
            System.err.println("Register error: " + registerTask.getException().getMessage());
            showError("Connection error. Please check your internet connection and try again.");
        });

        Thread thread = new Thread(registerTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSignInClicked() {
        try {
            TallyApp.showLoginScreen();
        } catch (IOException e) {
            showError("Failed to load login screen: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private String validate(String username, String email, String password, String confirm) {
        if (username.isBlank()) return "Username is required.";
        if (username.length() < 3) return "Username must be at least 3 characters.";
        if (username.length() > 50) return "Username must be 50 characters or fewer.";
        if (!username.matches("[a-zA-Z0-9_]+")) return "Username can only contain letters, numbers, and underscores.";

        if (email.isBlank()) return "Email is required.";
        // Matches: localpart@domain.tld — same pattern the backend's @Email annotation validates
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return "Please enter a valid email address.";

        if (password.isBlank()) return "Password is required.";
        if (password.length() < 8) return "Password must be at least 8 characters.";

        if (!password.equals(confirm)) return "Passwords do not match.";

        return null; // all valid
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void setLoading(boolean loading) {
        registerButton.setDisable(loading);
        registerButton.setText(loading ? "Creating account..." : "Create Account");
        usernameField.setDisable(loading);
        emailField.setDisable(loading);
        passwordField.setDisable(loading);
        confirmPasswordField.setDisable(loading);
    }
}
