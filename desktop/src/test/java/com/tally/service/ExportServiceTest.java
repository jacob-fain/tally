package com.tally.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tally.model.DailyLog;
import com.tally.model.Habit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExportService CSV and JSON export functionality.
 */
class ExportServiceTest {

    private ExportService exportService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        exportService = ExportService.getInstance();
        objectMapper = new ObjectMapper();
    }

    // =========================================================================
    // CSV Export Tests
    // =========================================================================

    @Test
    void shouldExportBasicCsvWithOneHabitAndOnLog() throws IOException {
        // Given
        Habit habit = createHabit(1L, "Morning Run");
        DailyLog log = createLog(1L, LocalDate.of(2024, 1, 15), true, null);

        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of(log));

        Path csvPath = tempDir.resolve("test.csv");

        // When
        exportService.exportToCsv(habits, logsMap, csvPath);

        // Then
        String csv = Files.readString(csvPath);
        String[] lines = csv.split("\n");

        assertEquals(2, lines.length, "Should have header + 1 data row");
        assertEquals("Habit Name,Date,Completed,Notes", lines[0]);
        assertEquals("Morning Run,2024-01-15,true,", lines[1]);
    }

    @Test
    void shouldEscapeCommasInCsvFields() throws IOException {
        // Given
        Habit habit = createHabit(1L, "Morning Run, Evening Walk");
        DailyLog log = createLog(1L, LocalDate.of(2024, 1, 15), true, "Felt good, great weather");

        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of(log));

        Path csvPath = tempDir.resolve("test.csv");

        // When
        exportService.exportToCsv(habits, logsMap, csvPath);

        // Then
        String csv = Files.readString(csvPath);
        String[] lines = csv.split("\n");

        // Both habit name and notes should be quoted because they contain commas
        assertTrue(lines[1].startsWith("\"Morning Run, Evening Walk\""));
        assertTrue(lines[1].contains("\"Felt good, great weather\""));
    }

    @Test
    void shouldEscapeQuotesInCsvFields() throws IOException {
        // Given
        Habit habit = createHabit(1L, "Read \"Atomic Habits\"");
        DailyLog log = createLog(1L, LocalDate.of(2024, 1, 15), true, "Finished \"Chapter 1\"");

        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of(log));

        Path csvPath = tempDir.resolve("test.csv");

        // When
        exportService.exportToCsv(habits, logsMap, csvPath);

        // Then
        String csv = Files.readString(csvPath);
        String[] lines = csv.split("\n");

        // Quotes should be doubled inside quoted strings
        assertTrue(lines[1].contains("\"Read \"\"Atomic Habits\"\"\""));
        assertTrue(lines[1].contains("\"Finished \"\"Chapter 1\"\"\""));
    }

    @Test
    void shouldEscapeNewlinesInCsvFields() throws IOException {
        // Given
        Habit habit = createHabit(1L, "Morning\nRoutine");
        DailyLog log = createLog(1L, LocalDate.of(2024, 1, 15), true, "Great day\nFelt amazing");

        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of(log));

        Path csvPath = tempDir.resolve("test.csv");

        // When
        exportService.exportToCsv(habits, logsMap, csvPath);

        // Then
        String csv = Files.readString(csvPath);
        // Should be quoted and preserve the newline
        assertTrue(csv.contains("\"Morning\nRoutine\""));
        assertTrue(csv.contains("\"Great day\nFelt amazing\""));
    }

    @Test
    void shouldHandleEmptyNotesInCsv() throws IOException {
        // Given
        Habit habit = createHabit(1L, "Morning Run");
        DailyLog log = createLog(1L, LocalDate.of(2024, 1, 15), true, null);

        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of(log));

        Path csvPath = tempDir.resolve("test.csv");

        // When
        exportService.exportToCsv(habits, logsMap, csvPath);

        // Then
        String csv = Files.readString(csvPath);
        String[] lines = csv.split("\n");

        // Notes field should be empty (not null string)
        assertTrue(lines[1].endsWith(",true,"));
    }

    @Test
    void shouldExportMultipleHabitsWithMultipleLogsInCsv() throws IOException {
        // Given
        Habit habit1 = createHabit(1L, "Morning Run");
        Habit habit2 = createHabit(2L, "Read");

        DailyLog log1 = createLog(1L, LocalDate.of(2024, 1, 15), true, "Great");
        DailyLog log2 = createLog(1L, LocalDate.of(2024, 1, 16), false, null);
        DailyLog log3 = createLog(2L, LocalDate.of(2024, 1, 15), true, "Chapter 5");

        List<Habit> habits = List.of(habit1, habit2);
        Map<Long, List<DailyLog>> logsMap = Map.of(
                1L, List.of(log1, log2),
                2L, List.of(log3)
        );

        Path csvPath = tempDir.resolve("test.csv");

        // When
        exportService.exportToCsv(habits, logsMap, csvPath);

        // Then
        String csv = Files.readString(csvPath);
        String[] lines = csv.split("\n");

        assertEquals(4, lines.length, "Should have header + 3 data rows");
        assertTrue(lines[1].contains("Morning Run"));
        assertTrue(lines[2].contains("Morning Run"));
        assertTrue(lines[3].contains("Read"));
    }

    @Test
    void shouldHandleHabitWithNoLogsInCsv() throws IOException {
        // Given
        Habit habit = createHabit(1L, "Morning Run");

        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of());

        Path csvPath = tempDir.resolve("test.csv");

        // When
        exportService.exportToCsv(habits, logsMap, csvPath);

        // Then
        String csv = Files.readString(csvPath);
        String[] lines = csv.split("\n");

        // Should only have header (no data rows for habit with no logs)
        assertEquals(1, lines.length);
        assertEquals("Habit Name,Date,Completed,Notes", lines[0]);
    }

    // =========================================================================
    // JSON Export Tests
    // =========================================================================

    @Test
    void shouldExportBasicJsonWithOneHabitAndOneLog() throws IOException {
        // Given
        Habit habit = createHabit(1L, "Morning Run");
        DailyLog log = createLog(1L, LocalDate.of(2024, 1, 15), true, "Great workout");

        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of(log));

        Path jsonPath = tempDir.resolve("test.json");

        // When
        exportService.exportToJson(habits, logsMap, jsonPath);

        // Then
        String json = Files.readString(jsonPath);
        JsonNode root = objectMapper.readTree(json);

        assertNotNull(root.get("exportDate"));
        JsonNode habitsArray = root.get("habits");
        assertEquals(1, habitsArray.size());

        JsonNode habitNode = habitsArray.get(0);
        assertEquals(1, habitNode.get("id").asLong());
        assertEquals("Morning Run", habitNode.get("name").asText());

        JsonNode logsArray = habitNode.get("logs");
        assertEquals(1, logsArray.size());

        JsonNode logNode = logsArray.get(0);
        assertEquals("2024-01-15", logNode.get("date").asText());
        assertTrue(logNode.get("completed").asBoolean());
        assertEquals("Great workout", logNode.get("notes").asText());
    }

    @Test
    void shouldIncludeAllHabitFieldsInJson() throws IOException {
        // Given
        Habit habit = new Habit();
        habit.setId(1L);
        habit.setName("Morning Run");
        habit.setDescription("Run 5k every morning");
        habit.setColor("#4CAF50");
        habit.setDisplayOrder(5);
        habit.setArchived(false);

        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of());

        Path jsonPath = tempDir.resolve("test.json");

        // When
        exportService.exportToJson(habits, logsMap, jsonPath);

        // Then
        String json = Files.readString(jsonPath);
        JsonNode root = objectMapper.readTree(json);
        JsonNode habitNode = root.get("habits").get(0);

        assertEquals(1, habitNode.get("id").asLong());
        assertEquals("Morning Run", habitNode.get("name").asText());
        assertEquals("Run 5k every morning", habitNode.get("description").asText());
        assertEquals("#4CAF50", habitNode.get("color").asText());
        assertEquals(5, habitNode.get("displayOrder").asInt());
        assertFalse(habitNode.get("archived").asBoolean());
    }

    @Test
    void shouldHandleNullNotesInJson() throws IOException {
        // Given
        Habit habit = createHabit(1L, "Morning Run");
        DailyLog log = createLog(1L, LocalDate.of(2024, 1, 15), true, null);

        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of(log));

        Path jsonPath = tempDir.resolve("test.json");

        // When
        exportService.exportToJson(habits, logsMap, jsonPath);

        // Then
        String json = Files.readString(jsonPath);
        JsonNode root = objectMapper.readTree(json);
        JsonNode logNode = root.get("habits").get(0).get("logs").get(0);

        assertTrue(logNode.get("notes").isNull());
    }

    @Test
    void shouldExportMultipleHabitsWithMultipleLogsInJson() throws IOException {
        // Given
        Habit habit1 = createHabit(1L, "Morning Run");
        Habit habit2 = createHabit(2L, "Read");

        DailyLog log1 = createLog(1L, LocalDate.of(2024, 1, 15), true, "Great");
        DailyLog log2 = createLog(1L, LocalDate.of(2024, 1, 16), false, null);
        DailyLog log3 = createLog(2L, LocalDate.of(2024, 1, 15), true, "Chapter 5");

        List<Habit> habits = List.of(habit1, habit2);
        Map<Long, List<DailyLog>> logsMap = Map.of(
                1L, List.of(log1, log2),
                2L, List.of(log3)
        );

        Path jsonPath = tempDir.resolve("test.json");

        // When
        exportService.exportToJson(habits, logsMap, jsonPath);

        // Then
        String json = Files.readString(jsonPath);
        JsonNode root = objectMapper.readTree(json);
        JsonNode habitsArray = root.get("habits");

        assertEquals(2, habitsArray.size());
        assertEquals(2, habitsArray.get(0).get("logs").size());
        assertEquals(1, habitsArray.get(1).get("logs").size());
    }

    @Test
    void shouldIncludeExportDateInJson() throws IOException {
        // Given
        Habit habit = createHabit(1L, "Morning Run");
        List<Habit> habits = List.of(habit);
        Map<Long, List<DailyLog>> logsMap = Map.of(1L, List.of());

        Path jsonPath = tempDir.resolve("test.json");

        // When
        exportService.exportToJson(habits, logsMap, jsonPath);

        // Then
        String json = Files.readString(jsonPath);
        JsonNode root = objectMapper.readTree(json);

        assertNotNull(root.get("exportDate"));
        // Should be today's date in ISO format
        String exportDate = root.get("exportDate").asText();
        assertTrue(exportDate.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private Habit createHabit(Long id, String name) {
        Habit habit = new Habit();
        habit.setId(id);
        habit.setName(name);
        habit.setColor("#4CAF50");
        habit.setDisplayOrder(0);
        habit.setArchived(false);
        return habit;
    }

    private DailyLog createLog(Long habitId, LocalDate date, boolean completed, String notes) {
        DailyLog log = new DailyLog();
        log.setHabitId(habitId);
        log.setLogDate(date);
        log.setCompleted(completed);
        log.setNotes(notes);
        return log;
    }
}
