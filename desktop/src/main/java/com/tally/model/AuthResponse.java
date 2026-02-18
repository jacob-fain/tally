package com.tally.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors the backend's AuthResponse DTO.
 *
 * The backend returns this on successful login/register:
 * {
 *   "accessToken": "eyJ...",
 *   "refreshToken": "eyJ...",
 *   "tokenType": "Bearer",
 *   "expiresIn": 900000,
 *   "user": {
 *     "id": 1,
 *     "username": "jacob",
 *     "email": "jacob@example.com",
 *     "createdAt": "..."
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserInfo user;

    public AuthResponse() {}

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public Long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }

    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
}
