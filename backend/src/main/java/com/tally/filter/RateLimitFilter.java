package com.tally.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
 * Bounded cache (Caffeine):
 * IP buckets are stored in a Caffeine cache capped at 10,000 entries.
 * Entries expire 2 minutes after last access, which:
 * - Prevents unbounded memory growth from unique IP flooding
 * - Naturally cleans up buckets for IPs that stop making requests
 *
 * X-Forwarded-For note:
 * We trust the X-Forwarded-For header to resolve the real client IP when
 * running behind Railway's reverse proxy. This is safe because Railway controls
 * the proxy layer and injects this header reliably. In a self-hosted setup
 * without a trusted proxy, this header could be spoofed — configure
 * server.tomcat.remoteip.trusted-proxies to restrict which proxies are trusted.
 *
 * Activation:
 * Enabled by default (rate-limit.enabled=true).
 * Set rate-limit.enabled=false in test profile to prevent test interference.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    static final int AUTH_REQUESTS_PER_MINUTE = 10;
    private static final int MAX_TRACKED_IPS = 10_000;
    private static final String RATE_LIMIT_EXCEEDED_JSON =
            "{\"status\":429,\"error\":\"Too Many Requests\","
            + "\"message\":\"Rate limit exceeded. Please try again later.\"}";

    // Caffeine cache: bounded at 10,000 IPs, expires buckets 2 min after last access.
    // This prevents memory DoS from an attacker generating thousands of unique IPs.
    private final Cache<String, Bucket> authBuckets = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_IPS)
            .expireAfterAccess(Duration.ofMinutes(2))
            .build();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (isAuthEndpoint(request)) {
            String clientIp = resolveClientIp(request);
            Bucket bucket = authBuckets.get(clientIp, ip -> createAuthBucket());

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

    boolean isAuthEndpoint(HttpServletRequest request) {
        // Skip rate limiting for CORS preflight requests.
        // Browsers send OPTIONS before the actual POST (e.g., login). Consuming tokens
        // on preflights would exhaust the bucket before the real request arrives.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return request.getRequestURI().startsWith("/api/auth/");
    }

    /**
     * Returns the client IP as resolved by the servlet container.
     *
     * In production (behind Railway), configure server.forward-headers-strategy=native
     * in application-prod.properties. Tomcat then parses X-Forwarded-For from trusted
     * proxies and sets getRemoteAddr() to the real client IP automatically.
     * We never parse X-Forwarded-For ourselves to avoid header spoofing.
     */
    String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private Bucket createAuthBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(AUTH_REQUESTS_PER_MINUTE)
                .refillGreedy(AUTH_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
