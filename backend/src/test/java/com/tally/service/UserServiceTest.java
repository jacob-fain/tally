package com.tally.service;

import com.tally.dto.request.LoginRequest;
import com.tally.dto.request.RefreshTokenRequest;
import com.tally.dto.request.RegisterRequest;
import com.tally.dto.response.AuthResponse;
import com.tally.dto.response.UserResponse;
import com.tally.exception.EmailAlreadyExistsException;
import com.tally.exception.InvalidCredentialsException;
import com.tally.exception.InvalidTokenException;
import com.tally.exception.UsernameAlreadyExistsException;
import com.tally.model.User;
import com.tally.repository.UserRepository;
import com.tally.security.CustomUserDetails;
import com.tally.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User existingUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("testuser", "test@example.com", "password123");
        loginRequest = new LoginRequest("testuser", "password123");
        existingUser = new User("testuser", "test@example.com", "$2a$10$hashedPassword");
    }

    @Test
    void registerUser_ValidRequest_CreatesUserWithHashedPassword() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(jwtTokenProvider.generateAccessToken(any(CustomUserDetails.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(900000L);

        AuthResponse response = userService.registerUser(registerRequest);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresIn());
        assertNotNull(response.getUser());
        assertEquals("testuser", response.getUser().getUsername());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("testuser", savedUser.getUsername());
        assertEquals("test@example.com", savedUser.getEmail());
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void registerUser_DuplicateUsername_ThrowsUsernameAlreadyExistsException() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThrows(UsernameAlreadyExistsException.class,
                () -> userService.registerUser(registerRequest));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_DuplicateEmail_ThrowsEmailAlreadyExistsException() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
                () -> userService.registerUser(registerRequest));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticateUser_ValidUsername_ReturnsAuthResponse() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(CustomUserDetails.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(900000L);

        AuthResponse response = userService.authenticateUser(loginRequest);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("testuser", response.getUser().getUsername());
    }

    @Test
    void authenticateUser_ValidEmail_ReturnsAuthResponse() {
        LoginRequest emailRequest = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByUsername("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(CustomUserDetails.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(900000L);

        AuthResponse response = userService.authenticateUser(emailRequest);

        assertNotNull(response);
        assertEquals("testuser", response.getUser().getUsername());
    }

    @Test
    void authenticateUser_InvalidPassword_ThrowsInvalidCredentialsException() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hashedPassword")).thenReturn(false);

        LoginRequest wrongPasswordRequest = new LoginRequest("testuser", "wrongpassword");
        assertThrows(InvalidCredentialsException.class,
                () -> userService.authenticateUser(wrongPasswordRequest));

        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void authenticateUser_NonExistentUser_ThrowsInvalidCredentialsException() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nonexistent")).thenReturn(Optional.empty());

        LoginRequest nonExistentRequest = new LoginRequest("nonexistent", "password123");
        assertThrows(InvalidCredentialsException.class,
                () -> userService.authenticateUser(nonExistentRequest));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void refreshToken_ValidToken_GeneratesNewTokens() {
        String validRefreshToken = "valid-refresh-token";
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(validRefreshToken);

        when(jwtTokenProvider.validateToken(validRefreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(validRefreshToken)).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
        when(jwtTokenProvider.generateAccessToken(any(CustomUserDetails.class))).thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(900000L);

        AuthResponse response = userService.refreshToken(refreshRequest);

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertEquals("testuser", response.getUser().getUsername());
    }

    @Test
    void refreshToken_InvalidToken_ThrowsInvalidTokenException() {
        String invalidToken = "invalid-token";
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(invalidToken);

        when(jwtTokenProvider.validateToken(invalidToken))
                .thenThrow(new InvalidTokenException("Invalid token"));

        assertThrows(InvalidTokenException.class,
                () -> userService.refreshToken(refreshRequest));

        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void getCurrentUser_ExistingUser_ReturnsUserResponseWithoutPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));

        UserResponse response = userService.getCurrentUser("testuser");

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertNotNull(response.getCreatedAt());
    }
}
