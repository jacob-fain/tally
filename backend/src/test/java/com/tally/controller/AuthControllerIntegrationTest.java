package com.tally.controller;

import com.tally.dto.request.LoginRequest;
import com.tally.dto.request.RefreshTokenRequest;
import com.tally.dto.request.RegisterRequest;
import com.tally.dto.response.AuthResponse;
import com.tally.dto.response.UserResponse;
import com.tally.model.User;
import com.tally.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_ValidRequest_Returns201AndTokens() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/register",
                request,
                AuthResponse.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getAccessToken());
        assertNotNull(response.getBody().getRefreshToken());
        assertEquals("Bearer", response.getBody().getTokenType());
        assertNotNull(response.getBody().getUser());
        assertEquals("testuser", response.getBody().getUser().getUsername());
        assertEquals("test@example.com", response.getBody().getUser().getEmail());
    }

    @Test
    void register_DuplicateUsername_Returns409() {
        User existingUser = new User("testuser", "existing@example.com", passwordEncoder.encode("password123"));
        userRepository.save(existingUser);

        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register",
                request,
                Map.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("testuser"));
    }

    @Test
    void register_DuplicateEmail_Returns409() {
        User existingUser = new User("existinguser", "test@example.com", passwordEncoder.encode("password123"));
        userRepository.save(existingUser);

        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register",
                request,
                Map.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("test@example.com"));
    }

    @Test
    void register_InvalidData_Returns400() {
        RegisterRequest request = new RegisterRequest("ab", "invalid-email", "short");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register",
                request,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation failed", response.getBody().get("message"));
    }

    @Test
    void login_ValidCredentials_Returns200AndTokens() {
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("password123"));
        userRepository.save(user);

        LoginRequest request = new LoginRequest("testuser", "password123");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/login",
                request,
                AuthResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getAccessToken());
        assertNotNull(response.getBody().getRefreshToken());
        assertEquals("testuser", response.getBody().getUser().getUsername());
    }

    @Test
    void login_InvalidPassword_Returns401() {
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("password123"));
        userRepository.save(user);

        LoginRequest request = new LoginRequest("testuser", "wrongpassword");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login",
                request,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_NonExistentUser_Returns401() {
        LoginRequest request = new LoginRequest("nonexistent", "password123");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login",
                request,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void refresh_ValidToken_Returns200AndNewTokens() {
        RegisterRequest registerRequest = new RegisterRequest("testuser", "test@example.com", "password123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                registerRequest,
                AuthResponse.class
        );
        String refreshToken = registerResponse.getBody().getRefreshToken();

        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/refresh",
                request,
                AuthResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getAccessToken());
        assertNotNull(response.getBody().getRefreshToken());
    }

    @Test
    void refresh_InvalidToken_Returns401() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/refresh",
                request,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void getCurrentUser_ValidToken_Returns200AndUserInfo() {
        RegisterRequest registerRequest = new RegisterRequest("testuser", "test@example.com", "password123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                registerRequest,
                AuthResponse.class
        );
        String accessToken = registerResponse.getBody().getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/auth/me",
                HttpMethod.GET,
                entity,
                UserResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("testuser", response.getBody().getUsername());
        assertEquals("test@example.com", response.getBody().getEmail());
    }

    @Test
    void getCurrentUser_NoToken_Returns403() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/auth/me",
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
