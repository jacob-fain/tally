package com.tally.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for Tally Backend.
 *
 * Current Phase 1 Setup:
 * - Health check endpoint (/api/health) is publicly accessible
 * - All other endpoints require HTTP Basic Authentication (temporary)
 * - In-memory user for testing (username: admin, password: admin)
 *
 * Phase 2 Plan:
 * - Replace HTTP Basic with JWT token authentication
 * - Add user registration and login endpoints
 * - Use BCrypt password hashing with database storage
 * - Implement refresh tokens for security
 *
 * Why permit health checks?
 * - Monitoring tools (Prometheus, Datadog) need unauthenticated access
 * - Load balancers check /api/health to route traffic
 * - Kubernetes liveness/readiness probes require quick access
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configure HTTP security rules.
     *
     * Security rules are evaluated top-to-bottom (first match wins):
     * 1. /api/health → Permit all (public access)
     * 2. All other requests → Require authentication
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (no authentication required)
                .requestMatchers("/api/health").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            // Enable HTTP Basic Authentication (temporary for Phase 1)
            .httpBasic(Customizer.withDefaults())
            // Disable CSRF for now (will be enabled with JWT in Phase 2)
            // Note: CSRF not needed for stateless JWT authentication
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /**
     * In-memory user for testing (Phase 1 only).
     *
     * WARNING: This is insecure and only for Phase 1 development!
     * Phase 2 will replace this with:
     * - Database-backed UserDetailsService
     * - BCrypt password hashing
     * - JWT token-based authentication
     *
     * SECURITY: This bean is ONLY active in dev and test profiles.
     * Production environments MUST implement proper authentication.
     *
     * Credentials:
     * - Username: admin
     * - Password: admin (plaintext, using {noop} prefix to disable encoding)
     */
    @Bean
    @Profile("!prod") // SECURITY: Prevent hardcoded credentials in production
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
            .username("admin")
            .password("{noop}admin") // {noop} = no password encoding (temporary!)
            .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(user);
    }
}
