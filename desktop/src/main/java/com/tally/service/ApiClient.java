package com.tally.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tally.model.ApiError;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Low-level HTTP client wrapping Java's built-in java.net.http.HttpClient.
 *
 * Why java.net.http.HttpClient instead of OkHttp?
 * - It's built into Java 17+ — zero extra dependencies
 * - Supports async (CompletableFuture) which we use in controllers
 * - Perfectly capable for our use case (simple REST calls)
 * - OkHttp shines for advanced caching, interceptors — overkill here
 *
 * This class is a singleton. Controllers and services call ApiClient.getInstance()
 * to get the shared instance. This is simpler than dependency injection for a
 * desktop app (we don't have Spring's IoC container here).
 *
 * Usage:
 *   ApiClient client = ApiClient.getInstance();
 *   HttpResponse<String> resp = client.post("/api/auth/login", body, null);
 */
public class ApiClient {

    // Base URL for the backend API.
    // In production this points to api.usetally.net.
    // For local development, override with the TALLY_API_URL environment variable:
    //   export TALLY_API_URL=http://localhost:9090
    private static final String DEFAULT_BASE_URL = "https://api.usetally.net";

    // Eager initialization — thread-safe without synchronization overhead.
    // The instance is created once when the class loads (on first use).
    private static final ApiClient instance = new ApiClient();

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private ApiClient() {
        this.baseUrl = System.getenv().getOrDefault("TALLY_API_URL", DEFAULT_BASE_URL);

        // HttpClient is thread-safe and should be reused (expensive to create)
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule()); // Handles LocalDate, Instant, etc.
    }

    public static ApiClient getInstance() {
        return instance;
    }

    // -------------------------------------------------------------------------
    // HTTP methods
    // -------------------------------------------------------------------------

    /**
     * Send a POST request with a JSON body.
     *
     * @param path        API path (e.g., "/api/auth/login")
     * @param body        Object to serialize as JSON request body
     * @param accessToken JWT token, or null for unauthenticated endpoints
     * @return HttpResponse with the raw response body as String
     */
    public HttpResponse<String> post(String path, Object body, String accessToken)
            throws IOException, InterruptedException {

        String json = objectMapper.writeValueAsString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));

        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Send a GET request.
     *
     * @param path        API path (e.g., "/api/habits")
     * @param accessToken JWT token (required for protected endpoints)
     * @return HttpResponse with the raw response body as String
     */
    public HttpResponse<String> get(String path, String accessToken)
            throws IOException, InterruptedException {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .GET();

        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Send a PUT request with a JSON body.
     */
    public HttpResponse<String> put(String path, Object body, String accessToken)
            throws IOException, InterruptedException {

        String json = objectMapper.writeValueAsString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json));

        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Send a DELETE request.
     */
    public HttpResponse<String> delete(String path, String accessToken)
            throws IOException, InterruptedException {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .DELETE();

        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // -------------------------------------------------------------------------
    // Response helpers
    // -------------------------------------------------------------------------

    /**
     * Deserialize a successful JSON response body into the given type.
     * Throws ApiException if deserialization fails.
     */
    public <T> T parseResponse(HttpResponse<String> response, Class<T> type) throws IOException {
        return objectMapper.readValue(response.body(), type);
    }

    /**
     * Parse an error response body into ApiError.
     * Returns a generic ApiError if parsing fails (e.g., HTML error pages).
     */
    public ApiError parseError(HttpResponse<String> response) {
        try {
            return objectMapper.readValue(response.body(), ApiError.class);
        } catch (IOException e) {
            ApiError fallback = new ApiError();
            fallback.setStatus(response.statusCode());
            // Truncate body to avoid exposing large HTML error pages or sensitive content
            String body = response.body();
            if (body == null || body.isBlank()) {
                fallback.setMessage("HTTP " + response.statusCode());
            } else {
                String truncated = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                fallback.setMessage("HTTP " + response.statusCode() + ": " + truncated);
            }
            return fallback;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
