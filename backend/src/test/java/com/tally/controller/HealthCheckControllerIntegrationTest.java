package com.tally.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for HealthCheckController.
 *
 * @SpringBootTest starts the full application context including:
 * - Web server (Tomcat) on a random port
 * - All controllers, services, and repositories
 * - Database connection (H2 in-memory for tests)
 * - Spring Security configuration
 *
 * Why integration tests?
 * - Test the complete request/response cycle
 * - Verify HTTP status codes and response format
 * - Ensure all Spring components work together
 * - Catch configuration errors that unit tests miss
 *
 * Difference from unit tests:
 * - Unit tests (@DataJpaTest): Fast, isolated, test single component
 * - Integration tests (@SpringBootTest): Slower, full context, test end-to-end
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthCheckControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Helper method to build full URL for API calls.
     */
    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/health";
    }

    @Test
    void shouldReturnHealthStatus() {
        // When: Call the health check endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity(getBaseUrl(), Map.class);

        // Then: Should return 200 OK with health status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("service")).isEqualTo("Tally Backend API");
        assertThat(response.getBody().get("timestamp")).isNotNull();
    }

    @Test
    void shouldReturnValidTimestamp() {
        // When: Call the health check endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity(getBaseUrl(), Map.class);

        // Then: Timestamp should be a valid ISO date-time string
        assertThat(response.getBody()).isNotNull();
        String timestamp = (String) response.getBody().get("timestamp");
        assertThat(timestamp).isNotBlank();
        assertThat(timestamp).contains("T"); // ISO format contains 'T' separator
        assertThat(timestamp).contains("2026"); // Current year
    }

    @Test
    void shouldReturnJsonContentType() {
        // When: Call the health check endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity(getBaseUrl(), Map.class);

        // Then: Content-Type should be application/json
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("application/json");
    }

    @Test
    void shouldBeAccessibleMultipleTimes() {
        // When: Call the endpoint multiple times
        ResponseEntity<Map> response1 = restTemplate.getForEntity(getBaseUrl(), Map.class);
        ResponseEntity<Map> response2 = restTemplate.getForEntity(getBaseUrl(), Map.class);
        ResponseEntity<Map> response3 = restTemplate.getForEntity(getBaseUrl(), Map.class);

        // Then: All calls should succeed with 200 OK
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);

        // And: Each response should have a status field
        assertThat(response1.getBody().get("status")).isEqualTo("UP");
        assertThat(response2.getBody().get("status")).isEqualTo("UP");
        assertThat(response3.getBody().get("status")).isEqualTo("UP");
    }
}
