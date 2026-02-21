package com.tally.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Habit and DailyLog Jackson deserialization.
 *
 * These models must deserialize cleanly from the backend API responses.
 * DailyLog uses LocalDate, which requires the JavaTimeModule to be registered.
 */
class HabitModelTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // =========================================================================
    // Habit deserialization
    // =========================================================================

    @Test
    void habitShouldDeserializeFromJson() throws Exception {
        String json = """
                {
                    "id": 42,
                    "name": "Morning Run",
                    "description": "Run 5k every morning",
                    "color": "#4CAF50",
                    "displayOrder": 1,
                    "archived": false
                }
                """;

        Habit habit = objectMapper.readValue(json, Habit.class);

        assertEquals(42L, habit.getId());
        assertEquals("Morning Run", habit.getName());
        assertEquals("Run 5k every morning", habit.getDescription());
        assertEquals("#4CAF50", habit.getColor());
        assertEquals(1, habit.getDisplayOrder());
        assertFalse(habit.isArchived());
    }

    @Test
    void habitShouldIgnoreUnknownFields() throws Exception {
        String json = """
                {
                    "id": 1,
                    "name": "Read",
                    "unknownFutureField": "some value",
                    "color": "#2196F3"
                }
                """;

        // Should not throw despite unknown field
        Habit habit = objectMapper.readValue(json, Habit.class);
        assertEquals(1L, habit.getId());
        assertEquals("Read", habit.getName());
    }

    @Test
    void habitSettersShouldWork() {
        Habit habit = new Habit();
        habit.setId(99L);
        habit.setName("Meditation");
        habit.setColor("#9C27B0");
        habit.setArchived(true);

        assertEquals(99L, habit.getId());
        assertEquals("Meditation", habit.getName());
        assertEquals("#9C27B0", habit.getColor());
        assertTrue(habit.isArchived());
    }

    // =========================================================================
    // DailyLog deserialization
    // =========================================================================

    @Test
    void dailyLogShouldDeserializeFromJson() throws Exception {
        String json = """
                {
                    "id": 7,
                    "habitId": 42,
                    "logDate": "2026-02-17",
                    "completed": true,
                    "notes": null
                }
                """;

        DailyLog log = objectMapper.readValue(json, DailyLog.class);

        assertEquals(7L, log.getId());
        assertEquals(42L, log.getHabitId());
        assertEquals(LocalDate.of(2026, 2, 17), log.getLogDate());
        assertTrue(log.isCompleted());
        assertNull(log.getNotes());
    }

    @Test
    void dailyLogShouldDeserializeIncompleteDays() throws Exception {
        String json = """
                {
                    "id": 8,
                    "habitId": 1,
                    "logDate": "2026-01-01",
                    "completed": false
                }
                """;

        DailyLog log = objectMapper.readValue(json, DailyLog.class);

        assertEquals(LocalDate.of(2026, 1, 1), log.getLogDate());
        assertFalse(log.isCompleted());
    }

    @Test
    void dailyLogShouldIgnoreUnknownFields() throws Exception {
        String json = """
                {
                    "id": 1,
                    "habitId": 1,
                    "logDate": "2025-12-31",
                    "completed": true,
                    "extraField": "ignored"
                }
                """;

        assertDoesNotThrow(() -> objectMapper.readValue(json, DailyLog.class));
    }

    @Test
    void dailyLogSettersShouldWork() {
        DailyLog log = new DailyLog();
        log.setId(5L);
        log.setHabitId(10L);
        log.setLogDate(LocalDate.of(2026, 6, 15));
        log.setCompleted(true);
        log.setNotes("Felt great");

        assertEquals(5L, log.getId());
        assertEquals(10L, log.getHabitId());
        assertEquals(LocalDate.of(2026, 6, 15), log.getLogDate());
        assertTrue(log.isCompleted());
        assertEquals("Felt great", log.getNotes());
    }
}
