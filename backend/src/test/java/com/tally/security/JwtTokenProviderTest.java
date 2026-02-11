package com.tally.security;

import com.tally.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private UserDetails userDetails;
    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm";
    private static final long ACCESS_TOKEN_EXPIRATION = 900000L; // 15 minutes
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 days
    private static final String ISSUER = "tally-api";

    @BeforeEach
    void setUp() {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"test"});

        jwtTokenProvider = new JwtTokenProvider(
                TEST_SECRET,
                ACCESS_TOKEN_EXPIRATION,
                REFRESH_TOKEN_EXPIRATION,
                ISSUER,
                mockEnvironment
        );
        userDetails = User.withUsername("testuser")
                .password("password")
                .authorities(new ArrayList<>())
                .build();
    }

    @Test
    void generateAccessToken_CreatesValidJwtStructure() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);

        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts: header.payload.signature");
    }

    @Test
    void generateAccessToken_ExpiresIn15Minutes() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);
        Claims claims = jwtTokenProvider.getClaimsFromToken(token);

        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        long actualExpiration = expiration.getTime() - issuedAt.getTime();

        assertEquals(ACCESS_TOKEN_EXPIRATION, actualExpiration, "Access token should expire in 15 minutes");
    }

    @Test
    void generateRefreshToken_ExpiresIn7Days() {
        String token = jwtTokenProvider.generateRefreshToken(userDetails);
        Claims claims = jwtTokenProvider.getClaimsFromToken(token);

        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        long actualExpiration = expiration.getTime() - issuedAt.getTime();

        assertEquals(REFRESH_TOKEN_EXPIRATION, actualExpiration, "Refresh token should expire in 7 days");
    }

    @Test
    void validateToken_ValidToken_ReturnsTrue() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_ExpiredToken_ThrowsInvalidTokenException() {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"test"});

        JwtTokenProvider shortExpiryProvider = new JwtTokenProvider(
                TEST_SECRET,
                1L, // 1 millisecond
                REFRESH_TOKEN_EXPIRATION,
                ISSUER,
                mockEnvironment
        );
        String token = shortExpiryProvider.generateAccessToken(userDetails);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThrows(InvalidTokenException.class, () -> jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_TamperedToken_ThrowsInvalidTokenException() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        assertThrows(InvalidTokenException.class, () -> jwtTokenProvider.validateToken(tamperedToken));
    }

    @Test
    void getUsernameFromToken_ValidToken_ReturnsUsername() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);

        String username = jwtTokenProvider.getUsernameFromToken(token);

        assertEquals("testuser", username);
    }

    @Test
    void getUsernameFromToken_InvalidToken_ThrowsInvalidTokenException() {
        String invalidToken = "invalid.token.string";

        assertThrows(InvalidTokenException.class, () -> jwtTokenProvider.getUsernameFromToken(invalidToken));
    }

    @Test
    void getClaimsFromToken_ValidToken_ReturnsCorrectClaims() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);

        Claims claims = jwtTokenProvider.getClaimsFromToken(token);

        assertEquals("testuser", claims.getSubject());
        assertEquals(ISSUER, claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void getClaimsFromToken_InvalidToken_ThrowsInvalidTokenException() {
        String invalidToken = "invalid.token.string";

        assertThrows(InvalidTokenException.class, () -> jwtTokenProvider.getClaimsFromToken(invalidToken));
    }

    @Test
    void getClaimsFromToken_TokenWithWrongSignature_ThrowsInvalidTokenException() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("different-secret-key-that-is-also-256-bits-long-for-testing".getBytes(StandardCharsets.UTF_8));
        String tokenWithWrongSignature = Jwts.builder()
                .subject("testuser")
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(wrongKey)
                .compact();

        assertThrows(InvalidTokenException.class, () -> jwtTokenProvider.getClaimsFromToken(tokenWithWrongSignature));
    }
}
