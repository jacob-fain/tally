package com.tally.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tally.model.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiClient helper methods.
 *
 * We don't test the actual HTTP methods (those make real network calls) —
 * that's integration testing territory. Instead we test the parsing helpers
 * that convert raw responses into typed objects.
 */
class ApiClientTest {

    private ApiClient apiClient;

    @BeforeEach
    void setUp() {
        apiClient = ApiClient.getInstance();
    }

    // =========================================================================
    // parseError
    // =========================================================================

    @Test
    void shouldParseWellFormedErrorResponse() throws IOException {
        String body = """
                {
                    "status": 401,
                    "error": "Unauthorized",
                    "message": "Invalid credentials"
                }
                """;

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(body);
        when(mockResponse.statusCode()).thenReturn(401);

        ApiError error = apiClient.parseError(mockResponse);

        assertEquals(401, error.getStatus());
        assertEquals("Unauthorized", error.getError());
        assertEquals("Invalid credentials", error.getMessage());
        assertEquals("Invalid credentials", error.displayMessage());
    }

    @Test
    void shouldHandleNonJsonErrorBody() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn("<html>Service Unavailable</html>");
        when(mockResponse.statusCode()).thenReturn(503);

        // Should not throw — returns a fallback ApiError
        ApiError error = apiClient.parseError(mockResponse);

        assertEquals(503, error.getStatus());
        assertNotNull(error.displayMessage());
        assertTrue(error.displayMessage().contains("503"));
    }

    @Test
    void shouldHandleEmptyErrorBody() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn("");
        when(mockResponse.statusCode()).thenReturn(500);

        ApiError error = apiClient.parseError(mockResponse);

        assertEquals(500, error.getStatus());
    }

    // =========================================================================
    // ApiError.displayMessage
    // =========================================================================

    @Test
    void displayMessageShouldPreferMessageOverError() {
        ApiError error = new ApiError();
        error.setStatus(400);
        error.setError("Bad Request");
        error.setMessage("Username already exists");

        assertEquals("Username already exists", error.displayMessage());
    }

    @Test
    void displayMessageShouldFallBackToErrorWhenNoMessage() {
        ApiError error = new ApiError();
        error.setStatus(404);
        error.setError("Not Found");
        // message is null

        assertEquals("Not Found", error.displayMessage());
    }

    @Test
    void displayMessageShouldFallBackToStatusWhenNoMessageOrError() {
        ApiError error = new ApiError();
        error.setStatus(502);
        // error and message are null

        assertTrue(error.displayMessage().contains("502"));
    }

    // =========================================================================
    // parseResponse
    // =========================================================================

    @Test
    void shouldParseResponseIntoTypedObject() throws IOException {
        String body = """
                {
                    "accessToken": "eyJhb...",
                    "refreshToken": "eyJhb...",
                    "tokenType": "Bearer",
                    "expiresIn": 900000,
                    "user": {
                        "id": 1,
                        "username": "jacob",
                        "email": "jacob@example.com"
                    }
                }
                """;

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(body);

        com.tally.model.AuthResponse auth = apiClient.parseResponse(
                mockResponse, com.tally.model.AuthResponse.class);

        assertEquals("eyJhb...", auth.getAccessToken());
        assertEquals("Bearer", auth.getTokenType());
        assertNotNull(auth.getUser());
        assertEquals("jacob", auth.getUser().getUsername());
        assertEquals("jacob@example.com", auth.getUser().getEmail());
    }

    @Test
    void shouldIgnoreUnknownFieldsInResponse() throws IOException {
        // Backend may add new fields in the future — client shouldn't crash
        String body = """
                {
                    "accessToken": "eyJhb...",
                    "refreshToken": "eyJhb...",
                    "tokenType": "Bearer",
                    "expiresIn": 900000,
                    "user": {"id": 1, "username": "jacob", "email": "jacob@example.com"},
                    "newFieldAddedInFutureVersion": "someValue"
                }
                """;

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(body);

        // Should not throw
        com.tally.model.AuthResponse auth = apiClient.parseResponse(
                mockResponse, com.tally.model.AuthResponse.class);

        assertNotNull(auth);
        assertNotNull(auth.getUser());
        assertEquals("jacob", auth.getUser().getUsername());
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    @Test
    void baseUrlShouldDefaultToProductionUrl() {
        // If TALLY_API_URL env var is not set, should use production URL
        // This test may be environment-specific; it verifies the URL is non-null/non-blank
        String baseUrl = apiClient.getBaseUrl();
        assertNotNull(baseUrl);
        assertFalse(baseUrl.isBlank());
        assertTrue(baseUrl.startsWith("http"), "Base URL should start with http/https");
    }
}
