package com.tally.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoint for monitoring application status.
 *
 * Used by:
 * - Load balancers (check if app is ready for traffic)
 * - Monitoring tools (Prometheus, Datadog, New Relic)
 * - Container orchestrators (Kubernetes liveness/readiness probes)
 * - CI/CD pipelines (verify deployment succeeded)
 *
 * Returns 200 OK if the application is running and healthy.
 */
@RestController
@RequestMapping("/api/health")
public class HealthCheckController {

    /**
     * Simple health check endpoint.
     * Returns application status and current timestamp.
     *
     * Response format:
     * {
     *   "status": "UP",
     *   "timestamp": "2026-02-11T12:15:00",
     *   "service": "Tally Backend API"
     * }
     *
     * HTTP Status Codes:
     * - 200 OK: Application is healthy and ready
     *
     * @return Health status response
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("service", "Tally Backend API");

        return ResponseEntity.ok(response);
    }
}
