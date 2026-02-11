package com.tally.security;

import com.tally.exception.InvalidTokenException;
import com.tally.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private User testUser;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        testUser = new User("testuser", "test@example.com", "$2a$10$hashedPassword");
        userDetails = new CustomUserDetails(testUser);
    }

    @Test
    void doFilterInternal_ValidToken_SetsAuthentication() throws ServletException, IOException {
        String token = "valid-jwt-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("testuser");
        when(customUserDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("testuser", ((CustomUserDetails) authentication.getPrincipal()).getUsername());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_InvalidToken_DoesNotSetAuthentication() throws ServletException, IOException {
        String token = "invalid-jwt-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenThrow(new InvalidTokenException("Invalid token"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_MissingToken_DoesNotSetAuthentication() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_MalformedAuthorizationHeader_DoesNotSetAuthentication() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("InvalidFormat token");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_EmptyBearerToken_DoesNotSetAuthentication() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_FilterChainContinuesAfterProcessing() throws ServletException, IOException {
        String token = "valid-jwt-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("testuser");
        when(customUserDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }
}
