package com.tally.security;

import com.tally.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String DEFAULT_JWT_SECRET = "changeme-default-256-bit-key-please-override-in-production";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;
    private final String secret;
    private final Environment environment;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${jwt.issuer}") String issuer,
            Environment environment) {
        this.secret = secret;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.issuer = issuer;
        this.environment = environment;
    }

    @PostConstruct
    public void validateJwtSecret() {
        // Check if running in production mode
        boolean isProduction = false;
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("prod".equals(profile)) {
                isProduction = true;
                break;
            }
        }

        // In production, reject the default JWT secret
        if (isProduction && DEFAULT_JWT_SECRET.equals(secret)) {
            String errorMessage = "SECURITY ERROR: Cannot start application in production mode with default JWT secret! " +
                    "Please set JWT_SECRET environment variable with a secure random key. " +
                    "Generate one with: openssl rand -base64 64";
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        // In non-production environments, warn if using default secret
        if (!isProduction && DEFAULT_JWT_SECRET.equals(secret)) {
            logger.warn("WARNING: Using default JWT secret key. This is INSECURE and only acceptable for local development. " +
                    "Set JWT_SECRET environment variable for production deployments.");
        }
    }

    public String generateAccessToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            throw new InvalidTokenException("Token has expired", ex);
        } catch (JwtException ex) {
            throw new InvalidTokenException("Invalid token", ex);
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getSubject();
        } catch (JwtException ex) {
            throw new InvalidTokenException("Could not extract username from token", ex);
        }
    }

    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new InvalidTokenException("Token has expired", ex);
        } catch (JwtException ex) {
            throw new InvalidTokenException("Invalid token", ex);
        }
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}
