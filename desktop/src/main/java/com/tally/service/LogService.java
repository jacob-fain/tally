package com.tally.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tally.model.DailyLog;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all daily log API calls.
 *
 * The key operation for the heatmap is toggleLog() — clicking a cell
 * calls this to flip a day between completed and incomplete.
 */
public class LogService {

    private static final LogService instance = new LogService();

    private final ApiClient apiClient;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    private LogService() {
        this.apiClient = ApiClient.getInstance();
        this.authService = AuthService.getInstance();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule()); // needed for LocalDate deserialization
    }

    public static LogService getInstance() {
        return instance;
    }

    /**
     * Fetch all logs for a habit within a date range.
     *
     * Used to populate the heatmap grid for a given year:
     *   getLogsForHabit(id, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
     */
    public List<DailyLog> getLogsForHabit(Long habitId, LocalDate startDate, LocalDate endDate)
            throws IOException, InterruptedException {

        String path = String.format("/api/logs?habitId=%d&startDate=%s&endDate=%s",
                habitId, startDate, endDate);

        HttpResponse<String> response = apiClient.get(path, authService.getAccessToken());
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), new TypeReference<List<DailyLog>>() {});
        }
        throw new IOException("Failed to fetch logs: " + response.statusCode());
    }

    /**
     * Toggle a day's completion status.
     *
     * If the day has no log yet → creates one with completed=true.
     * If it already exists → flips completed to the opposite.
     *
     * The backend's POST /api/logs uses upsert semantics (unique constraint
     * on habit_id + log_date), so we always POST regardless of whether a
     * log already exists. We pass the new desired state as the completed value.
     *
     * @param habitId   the habit to log for
     * @param date      the day to toggle
     * @param completed the new desired state (true = done, false = not done)
     * @return the saved DailyLog
     */
    public DailyLog setLog(Long habitId, LocalDate date, boolean completed)
            throws IOException, InterruptedException {

        Map<String, Object> body = new HashMap<>();
        body.put("habitId", habitId);
        body.put("logDate", date.toString()); // ISO-8601: "2026-02-17"
        body.put("completed", completed);

        HttpResponse<String> response = apiClient.post("/api/logs", body, authService.getAccessToken());
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return objectMapper.readValue(response.body(), DailyLog.class);
        }
        String errorMsg = apiClient.parseError(response).displayMessage();
        throw new IOException("Failed to save log: " + errorMsg);
    }
}
