package com.tally.service;

import com.tally.model.AuthResponse;
import com.tally.model.ApiError;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages authentication state and JWT token persistence.
 *
 * Responsibilities:
 * - Login / register via the backend API
 * - Store access + refresh tokens to disk (persists across app restarts)
 * - Load tokens on startup and check validity
 * - Provide the current access token to other services
 * - Logout (clear stored tokens)
 *
 * Token Storage:
 * Tokens are stored in ~/.tally/auth.dat as a simple key=value file.
 * The file is created with 600 permissions (owner read/write only).
 *
 * Why not OS keyring?
 * Cross-platform keyring (Windows Credential Manager, macOS Keychain, libsecret)
 * requires native libraries or complex JNI bindings. For Phase 5 we use a simple
 * file approach. We can migrate to OS keyring in a future phase if needed.
 *
 * Security note:
 * The token file is readable by the OS user only (chmod 600). This is acceptable
 * for a desktop app — if an attacker has read access to your home directory, they
 * can already access far more sensitive data. The main risk we're mitigating is
 * accidental exposure (e.g., sharing a screenshot of your files).
 */
public class AuthService {

    private static AuthService instance;

    private final ApiClient apiClient;
    private final Path tokenFilePath;

    // In-memory state (loaded from disk on startup, cleared on logout)
    private String accessToken;
    private String refreshToken;
    private String username;
    private String email;

    private AuthService() {
        this.apiClient = ApiClient.getInstance();
        this.tokenFilePath = Paths.get(System.getProperty("user.home"), ".tally", "auth.dat");
        loadTokensFromDisk();
    }

    public static AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Authentication operations
    // -------------------------------------------------------------------------

    /**
     * Authenticate with the backend. On success, tokens are stored to disk.
     *
     * @param usernameOrEmail username or email (backend accepts either)
     * @param password plaintext password (sent over HTTPS, never stored)
     * @return AuthResult with success flag and optional error message
     */
    public AuthResult login(String usernameOrEmail, String password)
            throws IOException, InterruptedException {

        Map<String, String> body = new HashMap<>();
        body.put("username", usernameOrEmail);
        body.put("password", password);

        HttpResponse<String> response = apiClient.post("/api/auth/login", body, null);

        if (response.statusCode() == 200) {
            AuthResponse auth = apiClient.parseResponse(response, AuthResponse.class);
            storeTokens(auth);
            return AuthResult.success();
        } else {
            ApiError error = apiClient.parseError(response);
            return AuthResult.failure(error.displayMessage());
        }
    }

    /**
     * Register a new account and authenticate immediately on success.
     *
     * @param username  desired username (3-50 chars, alphanumeric + underscores)
     * @param email     email address
     * @param password  password (backend enforces minimum strength)
     * @return AuthResult with success flag and optional error message
     */
    public AuthResult register(String username, String email, String password)
            throws IOException, InterruptedException {

        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);

        HttpResponse<String> response = apiClient.post("/api/auth/register", body, null);

        if (response.statusCode() == 201) {
            AuthResponse auth = apiClient.parseResponse(response, AuthResponse.class);
            storeTokens(auth);
            return AuthResult.success();
        } else {
            ApiError error = apiClient.parseError(response);
            return AuthResult.failure(error.displayMessage());
        }
    }

    /**
     * Attempt to refresh the access token using the stored refresh token.
     * Called automatically when an API call returns 401.
     *
     * @return true if refresh succeeded, false if refresh token is expired/invalid
     */
    public boolean refreshAccessToken() throws IOException, InterruptedException {
        if (refreshToken == null) return false;

        Map<String, String> body = new HashMap<>();
        body.put("refreshToken", refreshToken);

        HttpResponse<String> response = apiClient.post("/api/auth/refresh", body, null);

        if (response.statusCode() == 200) {
            AuthResponse auth = apiClient.parseResponse(response, AuthResponse.class);
            storeTokens(auth);
            return true;
        } else {
            // Refresh token is expired or invalid — user must log in again
            logout();
            return false;
        }
    }

    /**
     * Clear all stored tokens and in-memory state.
     * After this, isAuthenticated() returns false and the app shows the login screen.
     */
    public void logout() {
        this.accessToken = null;
        this.refreshToken = null;
        this.username = null;
        this.email = null;
        deleteTokenFile();
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    /**
     * Returns true if we have an access token stored.
     * Note: doesn't validate the token against the server — the token may be
     * expired. If an API call returns 401, call refreshAccessToken().
     */
    public boolean isAuthenticated() {
        return accessToken != null && !accessToken.isBlank();
    }

    public String getAccessToken() { return accessToken; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }

    // -------------------------------------------------------------------------
    // Token persistence
    // -------------------------------------------------------------------------

    private void storeTokens(AuthResponse auth) {
        this.accessToken = auth.getAccessToken();
        this.refreshToken = auth.getRefreshToken();
        if (auth.getUser() != null) {
            this.username = auth.getUser().getUsername();
            this.email = auth.getUser().getEmail();
        }
        saveTokensToDisk();
    }

    private void saveTokensToDisk() {
        try {
            Path dir = tokenFilePath.getParent();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            // Build a simple key=value file (not properties format — no escaping needed)
            String content = "accessToken=" + encode(accessToken) + "\n"
                    + "refreshToken=" + encode(refreshToken) + "\n"
                    + "username=" + encode(username) + "\n"
                    + "email=" + encode(email) + "\n";

            Files.writeString(tokenFilePath, content, StandardCharsets.UTF_8);

            // Restrict to owner-only read/write (unix permissions 600)
            // This won't work on Windows but is a no-op rather than an error
            try {
                Files.setPosixFilePermissions(tokenFilePath,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Windows doesn't support POSIX permissions — acceptable for now
            }

        } catch (IOException e) {
            // Non-fatal: tokens are still in memory for this session
            System.err.println("Warning: Could not save auth tokens to disk: " + e.getMessage());
        }
    }

    private void loadTokensFromDisk() {
        if (!Files.exists(tokenFilePath)) return;

        try {
            String content = Files.readString(tokenFilePath, StandardCharsets.UTF_8);
            Map<String, String> values = new HashMap<>();
            for (String line : content.split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String value = decode(line.substring(eq + 1).trim());
                    values.put(key, value);
                }
            }
            this.accessToken = values.get("accessToken");
            this.refreshToken = values.get("refreshToken");
            this.username = values.get("username");
            this.email = values.get("email");
        } catch (IOException e) {
            System.err.println("Warning: Could not load auth tokens from disk: " + e.getMessage());
        }
    }

    private void deleteTokenFile() {
        try {
            Files.deleteIfExists(tokenFilePath);
        } catch (IOException e) {
            System.err.println("Warning: Could not delete auth token file: " + e.getMessage());
        }
    }

    // Base64-encode token values to handle any special characters in the file
    private String encode(String value) {
        if (value == null) return "";
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Result type (avoids exceptions for expected failures like wrong password)
    // -------------------------------------------------------------------------

    /**
     * Represents the outcome of a login or register attempt.
     *
     * Why a result type instead of exceptions?
     * Wrong password and "username taken" are expected, recoverable conditions —
     * not exceptional ones. Using a result type keeps the calling code cleaner:
     *   AuthResult result = authService.login(username, password);
     *   if (result.isSuccess()) { ... } else { showError(result.getErrorMessage()); }
     *
     * vs throwing exceptions for normal control flow, which is an anti-pattern.
     */
    public static class AuthResult {
        private final boolean success;
        private final String errorMessage;

        private AuthResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static AuthResult success() {
            return new AuthResult(true, null);
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}
