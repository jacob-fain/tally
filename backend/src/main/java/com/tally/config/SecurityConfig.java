package com.tally.config;

import com.tally.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

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
 * Phase 4 Additions:
 * - CORS configuration (supports desktop app and future web clients)
 * - Actuator endpoints permitted for monitoring tools
 * - Swagger UI and OpenAPI spec permitted for API documentation
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

    /**
     * Comma-separated list of allowed CORS origins.
     * Default "*" allows all origins for development.
     * In production, set CORS_ALLOWED_ORIGINS to your frontend domain(s).
     *
     * Note: CORS is enforced by browsers, not Java HTTP clients.
     * The JavaFX desktop app doesn't need CORS, but web clients and
     * Swagger UI do. Configure this for future web frontend support.
     */
    @Value("${cors.allowed-origins:*}")
    private String corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Configure HTTP security rules with JWT authentication and CORS.
     *
     * Security rules are evaluated top-to-bottom (first match wins):
     * 1. /api/auth/register, /api/auth/login, /api/auth/refresh → Public
     * 2. /api/health → Public
     * 3. /actuator/** → Public (monitoring tools need unauthenticated access)
     * 4. /v3/api-docs/**, /swagger-ui/**, /swagger-ui.html → Public (API docs)
     * 5. All other /api/** requests → Require JWT authentication
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
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
     * CORS configuration allowing cross-origin requests.
     *
     * Why allowedOriginPatterns instead of allowedOrigins?
     * Spring Security rejects wildcard "*" with allowCredentials=true as a security
     * measure. allowedOriginPatterns supports "*" with credentials via pattern matching.
     *
     * The CORS_ALLOWED_ORIGINS env var accepts comma-separated origins:
     *   CORS_ALLOWED_ORIGINS=https://app.tally.com,https://admin.tally.com
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // allowCredentials is intentionally NOT set (defaults to false).
        // This API uses JWT in the Authorization header, not cookies.
        // allowCredentials(true) is only needed for cookie-based auth and
        // would prevent using wildcard origins as a security measure.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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
