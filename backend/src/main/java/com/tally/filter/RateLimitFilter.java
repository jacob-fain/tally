package com.tally.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiting filter for authentication endpoints using Bucket4j's token bucket algorithm.
 *
 * Why rate limit auth endpoints?
 * Without rate limiting, brute-force and credential stuffing attacks can try
 * thousands of password combinations per second. Rate limiting mitigates this
 * by limiting requests per IP per time window.
 *
 * Algorithm: Token bucket (Bucket4j)
 * - Each IP gets its own bucket with a fixed capacity of tokens
 * - Each request consumes one token
 * - Tokens refill at a steady rate (greedy refill: as fast as possible up to capacity)
 * - When the bucket is empty, requests return 429 Too Many Requests
 *
 * Current limits:
 * - Auth endpoints (/api/auth/**): 10 requests per minute per IP
 *
 * Why in-memory buckets?
 * This is a single-instance service for now. For horizontally scaled deployments,
 * use Bucket4j with Redis/Caffeine for distributed rate limiting.
 *
 * Why not limit all endpoints?
 * Auth endpoints are the primary brute-force target. General API endpoints are
 * protected by JWT authentication, so they require valid tokens anyway.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int AUTH_REQUESTS_PER_MINUTE = 10;
    private static final String RATE_LIMIT_EXCEEDED_JSON =
            "{\"status\":429,\"error\":\"Too Many Requests\","
            + "\"message\":\"Rate limit exceeded. Please try again later.\"}";

    // One bucket per IP address. ConcurrentHashMap is thread-safe for concurrent requests.
    private final ConcurrentMap<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (isAuthEndpoint(request)) {
            String clientIp = resolveClientIp(request);
            Bucket bucket = authBuckets.computeIfAbsent(clientIp, ip -> createAuthBucket());

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for IP {} on {}", clientIp, request.getRequestURI());
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(RATE_LIMIT_EXCEEDED_JSON);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/");
    }

    /**
     * Resolves the real client IP, respecting X-Forwarded-For for proxied requests.
     *
     * When deployed behind a reverse proxy (Railway, Nginx, etc.), the actual client
     * IP is in the X-Forwarded-For header, not the direct connection IP.
     * We take the first (leftmost) value which is the original client.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Creates a new token bucket for an IP address with the auth rate limit.
     *
     * Greedy refill: tokens are added as fast as possible up to capacity.
     * With 10 tokens / 60 seconds = ~1 token every 6 seconds.
     * A burst of 10 is allowed immediately, then throttled.
     */
    private Bucket createAuthBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(AUTH_REQUESTS_PER_MINUTE)
                .refillGreedy(AUTH_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
