package com.tally.service;

import com.tally.dto.request.BatchDailyLogRequest;
import com.tally.dto.request.CreateDailyLogRequest;
import com.tally.dto.response.DailyLogResponse;
import com.tally.exception.DailyLogNotFoundException;
import com.tally.exception.HabitNotFoundException;
import com.tally.exception.InvalidDateRangeException;
import com.tally.model.DailyLog;
import com.tally.model.Habit;
import com.tally.repository.DailyLogRepository;
import com.tally.repository.HabitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyLogServiceTest {

    @Mock
    private DailyLogRepository dailyLogRepository;

    @Mock
    private HabitRepository habitRepository;

    @InjectMocks
    private DailyLogService dailyLogService;

    private static final Long USER_ID = 1L;
    private static final Long HABIT_ID = 10L;
    private static final Long LOG_ID = 100L;

    private Habit habit;

    @BeforeEach
    void setUp() {
        habit = new Habit(USER_ID, "Morning Workout", null, null);
        setField(habit, "id", HABIT_ID);
        setField(habit, "createdAt", LocalDateTime.now().minusDays(30));
    }

    // =========================================================================
    // getLogsByDateRange
    // =========================================================================

    @Test
    void shouldGetLogsInDateRangeWhenHabitOwned() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 7);
        DailyLog log = new DailyLog(habit, LocalDate.of(2026, 1, 3), true, null);

        when(habitRepository.existsByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(true);
        when(dailyLogRepository.findByHabitIdAndLogDateBetweenOrderByLogDateAsc(HABIT_ID, start, end))
                .thenReturn(List.of(log));

        List<DailyLogResponse> result = dailyLogService.getLogsByDateRange(HABIT_ID, start, end, USER_ID);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2026, 1, 3), result.get(0).logDate());
    }

    @Test
    void shouldThrowWhenGettingLogsForUnownedHabit() {
        when(habitRepository.existsByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(false);

        assertThrows(HabitNotFoundException.class, () ->
                dailyLogService.getLogsByDateRange(HABIT_ID,
                        LocalDate.now().minusDays(7), LocalDate.now(), USER_ID));
    }

    @Test
    void shouldThrowWhenStartDateIsAfterEndDate() {
        when(habitRepository.existsByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(true);

        assertThrows(InvalidDateRangeException.class, () ->
                dailyLogService.getLogsByDateRange(HABIT_ID,
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), USER_ID));
    }

    // =========================================================================
    // getLogById
    // =========================================================================

    @Test
    void shouldGetLogByIdWhenOwned() {
        DailyLog log = new DailyLog(habit, LocalDate.now().minusDays(1), true, "note");
        setField(log, "id", LOG_ID);
        setField(log, "createdAt", LocalDateTime.now().minusDays(1));
        setField(log, "updatedAt", LocalDateTime.now().minusDays(1));

        when(dailyLogRepository.findByIdAndHabitUserId(LOG_ID, USER_ID)).thenReturn(Optional.of(log));

        DailyLogResponse result = dailyLogService.getLogById(LOG_ID, USER_ID);

        assertEquals(LOG_ID, result.id());
        assertTrue(result.completed());
    }

    @Test
    void shouldThrowWhenLogNotFoundOrNotOwned() {
        when(dailyLogRepository.findByIdAndHabitUserId(LOG_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(DailyLogNotFoundException.class, () ->
                dailyLogService.getLogById(LOG_ID, USER_ID));
    }

    // =========================================================================
    // createOrUpdateLog (upsert)
    // =========================================================================

    @Test
    void shouldCreateNewLogWhenNoneExistsForThatDate() {
        LocalDate logDate = LocalDate.now().minusDays(1);
        CreateDailyLogRequest request = new CreateDailyLogRequest(HABIT_ID, logDate, true, "great");

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
        when(dailyLogRepository.findByHabitIdAndLogDate(HABIT_ID, logDate)).thenReturn(Optional.empty());
        when(dailyLogRepository.save(any(DailyLog.class))).thenAnswer(inv -> {
            DailyLog saved = inv.getArgument(0);
            setField(saved, "id", LOG_ID);
            setField(saved, "createdAt", LocalDateTime.now());
            setField(saved, "updatedAt", LocalDateTime.now());
            return saved;
        });

        DailyLogResponse result = dailyLogService.createOrUpdateLog(request, USER_ID);

        assertTrue(result.completed());
        assertEquals(logDate, result.logDate());

        ArgumentCaptor<DailyLog> captor = ArgumentCaptor.forClass(DailyLog.class);
        verify(dailyLogRepository).save(captor.capture());
        assertEquals("great", captor.getValue().getNotes());
    }

    @Test
    void shouldUpdateExistingLogWhenOneExistsForThatDate() {
        LocalDate logDate = LocalDate.now().minusDays(1);
        CreateDailyLogRequest request = new CreateDailyLogRequest(HABIT_ID, logDate, false, "updated note");

        DailyLog existingLog = new DailyLog(habit, logDate, true, "old note");
        setField(existingLog, "id", LOG_ID);
        setField(existingLog, "createdAt", LocalDateTime.now().minusDays(1));
        setField(existingLog, "updatedAt", LocalDateTime.now().minusDays(1));

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
        when(dailyLogRepository.findByHabitIdAndLogDate(HABIT_ID, logDate))
                .thenReturn(Optional.of(existingLog));
        when(dailyLogRepository.save(any(DailyLog.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyLogResponse result = dailyLogService.createOrUpdateLog(request, USER_ID);

        assertFalse(result.completed());
        assertEquals("updated note", result.notes());
        // Verify we saved the existing log (updated), not a new one
        verify(dailyLogRepository).save(existingLog);
    }

    @Test
    void shouldThrowWhenCreatingLogForUnownedHabit() {
        LocalDate logDate = LocalDate.now().minusDays(1);
        CreateDailyLogRequest request = new CreateDailyLogRequest(HABIT_ID, logDate, true, null);

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(HabitNotFoundException.class, () ->
                dailyLogService.createOrUpdateLog(request, USER_ID));
    }

    // =========================================================================
    // deleteLog
    // =========================================================================

    @Test
    void shouldDeleteLogWhenOwned() {
        DailyLog log = new DailyLog(habit, LocalDate.now().minusDays(1), true, null);
        when(dailyLogRepository.findByIdAndHabitUserId(LOG_ID, USER_ID)).thenReturn(Optional.of(log));

        dailyLogService.deleteLog(LOG_ID, USER_ID);

        verify(dailyLogRepository).delete(log);
    }

    @Test
    void shouldThrowWhenDeletingUnownedLog() {
        when(dailyLogRepository.findByIdAndHabitUserId(LOG_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(DailyLogNotFoundException.class, () ->
                dailyLogService.deleteLog(LOG_ID, USER_ID));
    }

    // =========================================================================
    // batchCreateOrUpdateLogs
    // =========================================================================

    @Test
    void shouldBatchCreateMultipleLogs() {
        LocalDate day1 = LocalDate.now().minusDays(2);
        LocalDate day2 = LocalDate.now().minusDays(1);

        BatchDailyLogRequest request = new BatchDailyLogRequest(List.of(
                new CreateDailyLogRequest(HABIT_ID, day1, true, null),
                new CreateDailyLogRequest(HABIT_ID, day2, false, "tired")));

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
        when(dailyLogRepository.findByHabitIdAndLogDate(eq(HABIT_ID), any())).thenReturn(Optional.empty());
        when(dailyLogRepository.save(any(DailyLog.class))).thenAnswer(inv -> {
            DailyLog saved = inv.getArgument(0);
            setField(saved, "id", (long) (Math.random() * 1000));
            setField(saved, "createdAt", LocalDateTime.now());
            setField(saved, "updatedAt", LocalDateTime.now());
            return saved;
        });

        List<DailyLogResponse> results = dailyLogService.batchCreateOrUpdateLogs(request, USER_ID);

        assertEquals(2, results.size());
        verify(dailyLogRepository, times(2)).save(any(DailyLog.class));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            throw e;
        }
    }
}
