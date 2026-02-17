package com.tally.controller;

import com.tally.TallyApp;
import com.tally.service.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.io.IOException;

/**
 * Controller for the main window (main.fxml).
 *
 * Phase 5: Minimal placeholder — confirms login→main navigation works.
 * Phase 6 will replace this with the full heatmap view.
 */
public class MainController {

    @FXML private Label usernameLabel;

    private final AuthService authService = AuthService.getInstance();

    @FXML
    private void initialize() {
        String username = authService.getUsername();
        if (username != null) {
            usernameLabel.setText("Signed in as " + username);
        }
    }

    @FXML
    private void onLogoutClicked() {
        authService.logout();
        try {
            TallyApp.showLoginScreen();
        } catch (IOException e) {
            System.err.println("Failed to navigate to login screen: " + e.getMessage());
        }
    }
}
