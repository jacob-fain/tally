package com.tally.config;

import com.tally.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for Tally Backend.
 *
 * Phase 2 Setup (JWT Authentication):
 * - JWT token-based authentication (stateless)
 * - Public endpoints: /api/health, /api/auth/* (register, login, refresh)
 * - Protected endpoints: All other /api/** endpoints
 * - BCrypt password hashing with database storage
 * - Access tokens (15 min) and refresh tokens (7 days)
 *
 * Why permit health checks?
 * - Monitoring tools (Prometheus, Datadog) need unauthenticated access
 * - Load balancers check /api/health to route traffic
 * - Kubernetes liveness/readiness probes require quick access
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Configure HTTP security rules with JWT authentication.
     *
     * Security rules are evaluated top-to-bottom (first match wins):
     * 1. /api/auth/register, /api/auth/login, /api/auth/refresh → Public
     * 2. /api/health → Public
     * 3. All other /api/** requests → Require JWT authentication
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder for secure password hashing.
     * Uses default strength (10 rounds).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DEPRECATED: In-memory user for Phase 1 backwards compatibility.
     *
     * WARNING: This is no longer used in Phase 2 (JWT authentication)!
     * Kept only for potential backwards compatibility with Phase 1 tests.
     *
     * Phase 2 now uses:
     * - CustomUserDetailsService (database-backed)
     * - BCrypt password hashing
     * - JWT token-based authentication
     *
     * SECURITY: This bean is ONLY active when explicitly enabled.
     * It should NOT be used in normal operation.
     */
    @Bean
    @Profile("phase1-compat") // Only load if this profile is explicitly activated
    public UserDetailsService legacyUserDetailsService() {
        UserDetails user = User.builder()
            .username("admin")
            .password("{noop}admin")
            .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(user);
    }
}
