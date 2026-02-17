package com.tally.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Request logging configuration using Spring's built-in CommonsRequestLoggingFilter.
 *
 * Logs each incoming HTTP request with:
 * - HTTP method and URI
 * - Client IP address
 * - Query parameters
 *
 * Why no request body logging?
 * Request bodies may contain passwords, tokens, or PII. Never log them.
 *
 * Why no header logging?
 * Authorization headers contain JWT tokens. Logging them creates a security risk
 * since logs are often persisted to disk and accessible to multiple people.
 *
 * Activation: Requires the following log level to actually emit logs:
 *   logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter=DEBUG
 *
 * This is enabled in dev and disabled in prod (where we log at WARN level).
 */
@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeClientInfo(true);
        filter.setIncludePayload(false);  // Never log request bodies (may contain passwords)
        filter.setIncludeHeaders(false);  // Never log headers (contain JWT tokens)
        return filter;
    }
}
