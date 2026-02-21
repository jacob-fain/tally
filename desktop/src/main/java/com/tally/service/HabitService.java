package com.tally.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tally.model.Habit;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Handles all habit-related API calls.
 *
 * Each method throws IOException/InterruptedException — callers (controllers)
 * run these on a background Task and handle errors in onFailed callbacks.
 */
public class HabitService {

    private static final HabitService instance = new HabitService();

    private final ApiClient apiClient;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    private HabitService() {
        this.apiClient = ApiClient.getInstance();
        this.authService = AuthService.getInstance();
        this.objectMapper = new ObjectMapper();
    }

    public static HabitService getInstance() {
        return instance;
    }

    /**
     * Fetch all non-archived habits for the current user.
     */
    public List<Habit> getHabits() throws IOException, InterruptedException {
        HttpResponse<String> response = apiClient.get("/api/habits", authService.getAccessToken());
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), new TypeReference<List<Habit>>() {});
        }
        throw new IOException("Failed to fetch habits: " + response.statusCode());
    }

    /**
     * Create a new habit.
     *
     * @param name  habit name (required)
     * @param color hex color string e.g. "#4CAF50" (optional)
     */
    public Habit createHabit(String name, String color) throws IOException, InterruptedException {
        Map<String, String> body = Map.of(
                "name", name,
                "color", color != null ? color : "#4CAF50"
        );
        HttpResponse<String> response = apiClient.post("/api/habits", body, authService.getAccessToken());
        if (response.statusCode() == 201) {
            return objectMapper.readValue(response.body(), Habit.class);
        }
        String errorMsg = apiClient.parseError(response).displayMessage();
        throw new IOException("Failed to create habit: " + errorMsg);
    }

    /**
     * Delete a habit by ID.
     */
    public void deleteHabit(Long habitId) throws IOException, InterruptedException {
        HttpResponse<String> response = apiClient.delete(
                "/api/habits/" + habitId, authService.getAccessToken());
        if (response.statusCode() != 204 && response.statusCode() != 200) {
            throw new IOException("Failed to delete habit: " + response.statusCode());
        }
    }
}
