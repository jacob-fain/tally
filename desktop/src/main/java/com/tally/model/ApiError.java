package com.tally.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an error response from the backend API.
 *
 * The backend's GlobalExceptionHandler returns errors in this shape:
 * {
 *   "timestamp": "2026-02-17T12:00:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Username already exists"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiError {

    private int status;
    private String error;
    private String message;

    public ApiError() {}

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    /**
     * Returns a user-friendly error message, falling back to the HTTP error
     * description if no specific message is available.
     */
    public String displayMessage() {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return error != null ? error : "Unknown error (status " + status + ")";
    }
}
