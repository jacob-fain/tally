package com.tally.controller;

import com.tally.TallyApp;
import com.tally.service.AuthService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;

/**
 * Controller for the login screen (login.fxml).
 *
 * JavaFX MVC:
 * - View:       login.fxml (FXML layout)
 * - Controller: this class (event handlers, field access)
 * - Model:      AuthService (business logic, no JavaFX code)
 *
 * Threading: JavaFX is single-threaded. All UI updates must happen on the
 * JavaFX Application Thread (JAT). Network calls block the thread, so we
 * run them in a background Task and update the UI via Platform.runLater().
 *
 * javafx.concurrent.Task is JavaFX's equivalent of a background thread wrapper.
 * It's similar to SwingWorker if you've seen that before. It has lifecycle
 * callbacks (onSucceeded, onFailed) that automatically run on the JAT.
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;

    private final AuthService authService = AuthService.getInstance();

    /**
     * Called by FXMLLoader after all @FXML fields are injected.
     * Use this instead of a constructor for initialization that touches UI.
     */
    @FXML
    private void initialize() {
        // Allow pressing Enter in the password field to trigger login
        passwordField.setOnAction(event -> onLoginClicked());
    }

    @FXML
    private void onLoginClicked() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Client-side validation before hitting the network
        if (username.isBlank()) {
            showError("Please enter your username or email.");
            usernameField.requestFocus();
            return;
        }
        if (password.isBlank()) {
            showError("Please enter your password.");
            passwordField.requestFocus();
            return;
        }

        hideError();
        setLoading(true);

        // Run the network call on a background thread using JavaFX Task.
        //
        // Why Task instead of new Thread()?
        // Task integrates with JavaFX's threading model and provides
        // onSucceeded/onFailed callbacks that automatically execute on the JAT.
        // Using raw Thread + Platform.runLater() achieves the same thing but
        // with more boilerplate.
        Task<AuthService.AuthResult> loginTask = new Task<>() {
            @Override
            protected AuthService.AuthResult call() throws Exception {
                return authService.login(username, password);
            }
        };

        loginTask.setOnSucceeded(event -> {
            setLoading(false);
            AuthService.AuthResult result = loginTask.getValue();
            if (result.isSuccess()) {
                try {
                    TallyApp.showMainWindow();
                } catch (IOException e) {
                    showError("Failed to load main window: " + e.getMessage());
                }
            } else {
                showError(result.getErrorMessage());
                passwordField.clear();
                passwordField.requestFocus();
            }
        });

        loginTask.setOnFailed(event -> {
            setLoading(false);
            System.err.println("Login error: " + loginTask.getException().getMessage());
            showError("Connection error. Please check your internet connection and try again.");
        });

        // Run on a daemon thread (won't prevent app shutdown)
        Thread thread = new Thread(loginTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onCreateAccountClicked() {
        try {
            TallyApp.showRegisterScreen();
        } catch (IOException e) {
            showError("Failed to load register screen: " + e.getMessage());
        }
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

    /**
     * Disable form fields and show loading state while API call is in progress.
     * This prevents double-submits and gives visual feedback.
     *
     * Note: setManaged(false) removes the node from layout (it doesn't take up space).
     * setVisible(false) alone hides the node but keeps its space in the layout.
     */
    private void setLoading(boolean loading) {
        loginButton.setDisable(loading);
        loginButton.setText(loading ? "Signing in..." : "Sign In");
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
    }
}
