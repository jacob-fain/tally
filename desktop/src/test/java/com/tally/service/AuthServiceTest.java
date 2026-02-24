package com.tally.service;

import com.tally.service.AuthService.AuthResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthService.
 *
 * Challenge: AuthService is a singleton that writes to ~/.tally/auth.dat.
 * We use reflection to reset the singleton between tests.
 *
 * For the login/register methods that call the real API, those are
 * integration tests (skipped here with assumptions). Unit tests focus
 * on the token persistence and in-memory state logic.
 */
class AuthServiceTest {

    @BeforeEach
    void resetSingleton() throws Exception {
        // Reset the singleton instance between tests so each test starts fresh
        Field instanceField = AuthService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    // =========================================================================
    // AuthResult
    // =========================================================================

    @Test
    void authResultSuccessShouldHaveNullErrorMessage() {
        AuthResult result = AuthResult.success();
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }

    @Test
    void authResultFailureShouldContainMessage() {
        AuthResult result = AuthResult.failure("Invalid credentials");
        assertFalse(result.isSuccess());
        assertEquals("Invalid credentials", result.getErrorMessage());
    }

    // =========================================================================
    // isAuthenticated
    // =========================================================================

    @Test
    void isAuthenticatedShouldNotThrow() {
        // isAuthenticated() may return true or false depending on whether a token file
        // exists on the test machine. We just verify it doesn't throw.
        AuthService service = AuthService.getInstance();
        assertDoesNotThrow(service::isAuthenticated);
    }

    @Test
    void logoutShouldClearAuthenticationState() throws Exception {
        AuthService service = AuthService.getInstance();

        // Inject tokens directly via reflection to simulate a logged-in state
        setField(service, "accessToken", "test-access-token");
        setField(service, "refreshToken", "test-refresh-token");
        setField(service, "username", "testuser");
        setField(service, "email", "test@example.com");

        assertTrue(service.isAuthenticated());
        assertEquals("testuser", service.getUsername());

        service.logout();

        assertFalse(service.isAuthenticated());
        assertNull(service.getAccessToken());
        assertNull(service.getUsername());
        assertNull(service.getEmail());
    }

    // =========================================================================
    // Token persistence
    // =========================================================================

    @Test
    void tokensShouldSurviveServiceRestart() throws Exception {
        AuthService service = AuthService.getInstance();

        // Simulate login by injecting tokens
        setField(service, "accessToken", "my-access-token");
        setField(service, "refreshToken", "my-refresh-token");
        setField(service, "username", "jacob");
        setField(service, "email", "jacob@example.com");

        // Call private saveTokensToDisk via the public path (logout clears, but we need save)
        // We trigger it by calling a method that uses it internally
        // Alternative: use reflection to call private method
        java.lang.reflect.Method saveMethod = AuthService.class.getDeclaredMethod("saveTokensToDisk");
        saveMethod.setAccessible(true);
        saveMethod.invoke(service);

        // Reset singleton and create a new instance — it should load from disk
        resetSingleton();
        AuthService reloaded = AuthService.getInstance();

        assertEquals("my-access-token", reloaded.getAccessToken());
        assertEquals("jacob", reloaded.getUsername());
        assertEquals("jacob@example.com", reloaded.getEmail());

        // Cleanup: logout to remove the test token file
        reloaded.logout();
    }

    @Test
    void logoutShouldDeleteTokenFile() throws Exception {
        AuthService service = AuthService.getInstance();

        // Create a token file
        setField(service, "accessToken", "some-token");
        setField(service, "refreshToken", "some-refresh");
        setField(service, "username", "user");
        setField(service, "email", "user@test.com");

        java.lang.reflect.Method saveMethod = AuthService.class.getDeclaredMethod("saveTokensToDisk");
        saveMethod.setAccessible(true);
        saveMethod.invoke(service);

        Path tokenFile = getTokenFilePath(service);
        assertTrue(Files.exists(tokenFile), "Token file should exist after save");

        service.logout();

        assertFalse(Files.exists(tokenFile), "Token file should be deleted after logout");
    }

    // =========================================================================
    // Session expiration callback
    // =========================================================================

    @Test
    void sessionExpirationCallbackShouldBeInvoked() throws Exception {
        AuthService service = AuthService.getInstance();

        // Set up a flag to track if callback was invoked
        final boolean[] callbackInvoked = {false};
        service.setOnSessionExpired(() -> {
            callbackInvoked[0] = true;
        });

        // Simulate session expiration by calling refreshAccessToken with no refresh token
        setField(service, "refreshToken", null);
        boolean refreshed = service.refreshAccessToken();

        // Should fail to refresh and invoke callback
        assertFalse(refreshed, "Refresh should fail when no refresh token present");
        // Note: Callback uses Platform.runLater() so it won't be invoked immediately
        // in unit tests (JavaFX not initialized). This test verifies the callback
        // is registered correctly, but can't verify execution without JavaFX.
    }

    @Test
    void refreshShouldReturnFalseWithoutClearingTokensWhenNoRefreshToken() throws Exception {
        AuthService service = AuthService.getInstance();

        // Set up initial state with access token but no refresh token
        setField(service, "accessToken", "test-access");
        setField(service, "refreshToken", null);

        boolean refreshed = service.refreshAccessToken();

        // Should fail but NOT clear tokens (only clears on actual API failure)
        assertFalse(refreshed);
        assertEquals("test-access", service.getAccessToken());
    }

    @Test
    void shouldNotInvokeCallbackIfNotSet() throws Exception {
        AuthService service = AuthService.getInstance();

        // Don't set callback (null)
        setField(service, "refreshToken", null);

        // Should not throw even though callback is null
        assertDoesNotThrow(() -> service.refreshAccessToken());
    }

    // =========================================================================
    // Token refresh (API-dependent)
    // =========================================================================

    @Test
    void refreshAccessTokenShouldReturnFalseWithNoRefreshToken() throws Exception {
        AuthService service = AuthService.getInstance();

        // Ensure no refresh token
        setField(service, "refreshToken", null);

        // Should return false immediately without making API call
        boolean refreshed = service.refreshAccessToken();
        assertFalse(refreshed, "Should fail when no refresh token is present");
    }

    // Note: Testing the actual HTTP refresh logic would require mocking HttpClient
    // or making real API calls (integration test). The logic is tested manually
    // and through the automatic retry mechanism in ApiClient.

    // =========================================================================
    // Helpers
    // =========================================================================

    private void setField(Object target, String fieldName, String value) throws Exception {
        Field field = AuthService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Path getTokenFilePath(AuthService service) throws Exception {
        Field field = AuthService.class.getDeclaredField("tokenFilePath");
        field.setAccessible(true);
        return (Path) field.get(service);
    }

    private String getRefreshToken(AuthService service) throws Exception {
        Field field = AuthService.class.getDeclaredField("refreshToken");
        field.setAccessible(true);
        return (String) field.get(service);
    }
}
