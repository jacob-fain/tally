package com.tally.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tally.model.DailyLog;
import com.tally.model.Habit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for exporting habit and log data to various formats.
 *
 * Supports:
 * - CSV: Simple spreadsheet format with one row per habit-day combination
 * - JSON: Structured format with habits and nested logs
 */
public class ExportService {
    private static final ExportService INSTANCE = new ExportService();

    private final ObjectMapper objectMapper;

    private ExportService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static ExportService getInstance() {
        return INSTANCE;
    }

    /**
     * Export data to CSV format.
     *
     * CSV format:
     * Habit Name,Date,Completed,Notes
     * Morning Workout,2024-01-01,true,
     * Morning Workout,2024-01-02,false,
     * ...
     *
     * @param habits List of habits to export
     * @param logsMap Map of habit ID to logs
     * @param filePath Where to save the CSV file
     * @throws IOException if file write fails
     */
    public void exportToCsv(List<Habit> habits, Map<Long, List<DailyLog>> logsMap, Path filePath) throws IOException {
        StringBuilder csv = new StringBuilder();

        // Header
        csv.append("Habit Name,Date,Completed,Notes\n");

        // Data rows
        for (Habit habit : habits) {
            List<DailyLog> logs = logsMap.getOrDefault(habit.getId(), List.of());

            for (DailyLog log : logs) {
                csv.append(escapeCsv(habit.getName())).append(",");
                csv.append(log.getLogDate()).append(",");
                csv.append(log.isCompleted()).append(",");
                csv.append(escapeCsv(log.getNotes() != null ? log.getNotes() : ""));
                csv.append("\n");
            }
        }

        Files.writeString(filePath, csv.toString());
    }

    /**
     * Export data to JSON format.
     *
     * JSON format:
     * {
     *   "exportDate": "2024-01-15",
     *   "habits": [
     *     {
     *       "id": 1,
     *       "name": "Morning Workout",
     *       "color": "#4CAF50",
     *       "logs": [
     *         {"date": "2024-01-01", "completed": true, "notes": null},
     *         {"date": "2024-01-02", "completed": false, "notes": "Felt sick"}
     *       ]
     *     }
     *   ]
     * }
     *
     * @param habits List of habits to export
     * @param logsMap Map of habit ID to logs
     * @param filePath Where to save the JSON file
     * @throws IOException if file write fails
     */
    public void exportToJson(List<Habit> habits, Map<Long, List<DailyLog>> logsMap, Path filePath) throws IOException {
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("exportDate", LocalDate.now().toString());
        exportData.put("habits", buildHabitsWithLogs(habits, logsMap));

        String json = objectMapper.writeValueAsString(exportData);
        Files.writeString(filePath, json);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Build a list of habit objects with nested logs for JSON export.
     */
    private List<Map<String, Object>> buildHabitsWithLogs(List<Habit> habits, Map<Long, List<DailyLog>> logsMap) {
        return habits.stream().map(habit -> {
            Map<String, Object> habitData = new HashMap<>();
            habitData.put("id", habit.getId());
            habitData.put("name", habit.getName());
            habitData.put("description", habit.getDescription());
            habitData.put("color", habit.getColor());
            habitData.put("displayOrder", habit.getDisplayOrder());
            habitData.put("archived", habit.isArchived());

            List<DailyLog> logs = logsMap.getOrDefault(habit.getId(), List.of());
            List<Map<String, Object>> logsData = logs.stream().map(log -> {
                Map<String, Object> logData = new HashMap<>();
                logData.put("date", log.getLogDate().toString());
                logData.put("completed", log.isCompleted());
                logData.put("notes", log.getNotes());
                return logData;
            }).toList();

            habitData.put("logs", logsData);
            return habitData;
        }).toList();
    }

    /**
     * Escape CSV special characters (quotes, commas, newlines).
     */
    private String escapeCsv(String value) {
        if (value == null) return "";

        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }
}
