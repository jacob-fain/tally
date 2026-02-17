package com.tally.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors the backend's UserResponse DTO.
 * Nested inside AuthResponse as the "user" field.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo {

    private Long id;
    private String username;
    private String email;

    public UserInfo() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
