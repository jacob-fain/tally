package com.tally;

import com.tally.service.AuthService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main entry point for the Tally desktop application.
 *
 * JavaFX requires that Application.launch() be called from a class that
 * extends Application. This class bootstraps the JavaFX runtime and loads
 * the appropriate initial screen based on auth state.
 *
 * Launch order:
 * 1. main() calls Application.launch()
 * 2. JavaFX runtime calls start(Stage) on the JavaFX Application Thread
 * 3. We check if a valid JWT token is stored
 * 4. If yes → go straight to the main window (Phase 6)
 * 5. If no → show the login screen
 */
public class TallyApp extends Application {

    // The single primary Stage (window) — we keep a reference so
    // controllers can swap scenes (e.g., login → register → main).
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        primaryStage.setTitle("Tally");
        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(500);
        primaryStage.setResizable(false);

        // Route to login or main based on stored JWT validity
        if (AuthService.getInstance().isAuthenticated()) {
            showMainWindow();
        } else {
            showLoginScreen();
        }

        primaryStage.show();
    }

    // -------------------------------------------------------------------------
    // Navigation helpers — called by controllers to swap screens
    // -------------------------------------------------------------------------

    /**
     * Replace the current scene with the login screen.
     * Called by: RegisterController (back to login), MainController (logout)
     */
    public static void showLoginScreen() throws IOException {
        Scene scene = loadScene("/com/tally/login.fxml", 400, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Tally — Sign In");
        primaryStage.setResizable(false);
    }

    /**
     * Replace the current scene with the register screen.
     * Called by: LoginController ("Create account" link)
     */
    public static void showRegisterScreen() throws IOException {
        Scene scene = loadScene("/com/tally/register.fxml", 400, 560);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Tally — Create Account");
        primaryStage.setResizable(false);
    }

    /**
     * Replace the current scene with the main heatmap window.
     * Called after successful login or register, and on startup if token valid.
     */
    public static void showMainWindow() throws IOException {
        Scene scene = loadScene("/com/tally/main.fxml", 960, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Tally");
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(400);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static Scene loadScene(String fxmlPath, int width, int height) throws IOException {
        FXMLLoader loader = new FXMLLoader(TallyApp.class.getResource(fxmlPath));
        return new Scene(loader.load(), width, height);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
