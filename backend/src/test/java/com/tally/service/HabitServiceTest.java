package com.tally.service;

import com.tally.dto.request.CreateHabitRequest;
import com.tally.dto.request.ReorderHabitsRequest;
import com.tally.dto.request.UpdateHabitRequest;
import com.tally.dto.response.HabitResponse;
import com.tally.dto.response.HabitStatsResponse;
import com.tally.dto.response.HeatmapResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private DailyLogRepository dailyLogRepository;

    @InjectMocks
    private HabitService habitService;

    private static final Long USER_ID = 1L;
    private static final Long HABIT_ID = 10L;

    private Habit activeHabit;
    private Habit archivedHabit;

    @BeforeEach
    void setUp() {
        activeHabit = new Habit(USER_ID, "Morning Workout", "30 min workout", "#3498db");
        setField(activeHabit, "id", HABIT_ID);
        setField(activeHabit, "archived", false);
        setField(activeHabit, "displayOrder", 0);
        setField(activeHabit, "createdAt", LocalDateTime.now().minusDays(30));

        archivedHabit = new Habit(USER_ID, "Old Habit", null, null);
        setField(archivedHabit, "id", 11L);
        setField(archivedHabit, "archived", true);
        setField(archivedHabit, "archivedAt", LocalDateTime.now().minusDays(5));
        setField(archivedHabit, "displayOrder", 1);
        setField(archivedHabit, "createdAt", LocalDateTime.now().minusDays(60));
    }

    // =========================================================================
    // CRUD tests
    // =========================================================================

    @Test
    void shouldGetOnlyActiveHabitsByDefault() {
        when(habitRepository.findByUserIdAndArchivedFalseOrderByDisplayOrderAsc(USER_ID))
                .thenReturn(List.of(activeHabit));

        List<HabitResponse> result = habitService.getAllHabits(USER_ID, false);

        assertEquals(1, result.size());
        assertEquals("Morning Workout", result.get(0).name());
        verify(habitRepository).findByUserIdAndArchivedFalseOrderByDisplayOrderAsc(USER_ID);
    }

    @Test
    void shouldGetAllHabitsIncludingArchivedWhenRequested() {
        when(habitRepository.findByUserIdOrderByDisplayOrderAsc(USER_ID))
                .thenReturn(List.of(activeHabit, archivedHabit));

        List<HabitResponse> result = habitService.getAllHabits(USER_ID, true);

        assertEquals(2, result.size());
        verify(habitRepository).findByUserIdOrderByDisplayOrderAsc(USER_ID);
    }

    @Test
    void shouldGetHabitByIdWhenUserOwnsIt() {
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID))
                .thenReturn(Optional.of(activeHabit));

        HabitResponse result = habitService.getHabitById(HABIT_ID, USER_ID);

        assertEquals(HABIT_ID, result.id());
        assertEquals("Morning Workout", result.name());
    }

    @Test
    void shouldThrowExceptionWhenHabitNotFound() {
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(HabitNotFoundException.class, () ->
                habitService.getHabitById(HABIT_ID, USER_ID));
    }

    @Test
    void shouldCreateHabitWithCorrectFields() {
        CreateHabitRequest request = new CreateHabitRequest("Read 30 min", "Read a book", "#e74c3c");
        when(habitRepository.save(any(Habit.class))).thenAnswer(inv -> inv.getArgument(0));

        HabitResponse result = habitService.createHabit(request, USER_ID);

        assertEquals("Read 30 min", result.name());
        assertEquals("Read a book", result.description());
        assertEquals("#e74c3c", result.color());

        ArgumentCaptor<Habit> captor = ArgumentCaptor.forClass(Habit.class);
        verify(habitRepository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    @Test
    void shouldUpdateHabitFieldsWhenUserOwnsIt() {
        UpdateHabitRequest request = new UpdateHabitRequest("Evening Workout", "Updated desc", "#2ecc71");
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID))
                .thenReturn(Optional.of(activeHabit));
        when(habitRepository.save(any(Habit.class))).thenAnswer(inv -> inv.getArgument(0));

        HabitResponse result = habitService.updateHabit(HABIT_ID, request, USER_ID);

        assertEquals("Evening Workout", result.name());
        assertEquals("Updated desc", result.description());
        assertEquals("#2ecc71", result.color());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonOwnedHabit() {
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(HabitNotFoundException.class, () ->
                habitService.updateHabit(HABIT_ID, new UpdateHabitRequest("x", null, null), USER_ID));
    }

    @Test
    void shouldHardDeleteHabit() {
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID))
                .thenReturn(Optional.of(activeHabit));

        habitService.deleteHabit(HABIT_ID, USER_ID);

        verify(habitRepository).delete(activeHabit);
        verify(habitRepository, never()).save(any(Habit.class));
    }

    @Test
    void shouldReorderHabitsForUser() {
        Habit habit2 = new Habit(USER_ID, "Meditation", null, null);
        setField(habit2, "id", 11L);

        ReorderHabitsRequest request = new ReorderHabitsRequest(List.of(
                new ReorderHabitsRequest.HabitOrderItem(HABIT_ID, 1),
                new ReorderHabitsRequest.HabitOrderItem(11L, 0)));

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        when(habitRepository.findByIdAndUserId(11L, USER_ID)).thenReturn(Optional.of(habit2));
        when(habitRepository.save(any(Habit.class))).thenAnswer(inv -> inv.getArgument(0));

        habitService.reorderHabits(request, USER_ID);

        verify(habitRepository, times(2)).save(any(Habit.class));
    }

    // =========================================================================
    // Streak calculation tests (critical business logic)
    // =========================================================================

    @Test
    void shouldReturnZeroStreakWhenNoLogs() {
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        when(dailyLogRepository.findByHabitIdOrderByLogDateDesc(HABIT_ID)).thenReturn(List.of());

        HabitStatsResponse stats = habitService.getHabitStats(HABIT_ID, USER_ID);

        assertEquals(0, stats.currentStreak());
        assertEquals(0, stats.longestStreak());
    }

    @Test
    void shouldReturnZeroStreakWhenYesterdayWasNotCompleted() {
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        when(dailyLogRepository.findByHabitIdOrderByLogDateDesc(HABIT_ID))
                .thenReturn(List.of(makeLog(LocalDate.now().minusDays(1), false)));

        HabitStatsResponse stats = habitService.getHabitStats(HABIT_ID, USER_ID);

        assertEquals(0, stats.currentStreak());
    }

    @Test
    void shouldReturnStreakOfOneWhenOnlyTodayIsCompleted() {
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        // Only today's log exists, no yesterday log
        when(dailyLogRepository.findByHabitIdOrderByLogDateDesc(HABIT_ID))
                .thenReturn(List.of(makeLog(LocalDate.now(), true)));

        HabitStatsResponse stats = habitService.getHabitStats(HABIT_ID, USER_ID);

        // Today is completed so streak = 1 (today counts as the anchor)
        assertEquals(1, stats.currentStreak());
    }

    @Test
    void shouldCalculateCurrentStreakOfOne() {
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        when(dailyLogRepository.findByHabitIdOrderByLogDateDesc(HABIT_ID))
                .thenReturn(List.of(makeLog(LocalDate.now().minusDays(1), true)));

        HabitStatsResponse stats = habitService.getHabitStats(HABIT_ID, USER_ID);

        assertEquals(1, stats.currentStreak());
    }

    @Test
    void shouldCalculateCurrentStreakAcrossMultipleConsecutiveDays() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<DailyLog> logs = List.of(
                makeLog(yesterday, true),
                makeLog(yesterday.minusDays(1), true),
                makeLog(yesterday.minusDays(2), true));

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        when(dailyLogRepository.findByHabitIdOrderByLogDateDesc(HABIT_ID)).thenReturn(logs);

        HabitStatsResponse stats = habitService.getHabitStats(HABIT_ID, USER_ID);

        assertEquals(3, stats.currentStreak());
    }

    @Test
    void shouldBreakCurrentStreakOnGap() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // yesterday and 3 days ago completed, but 2 days ago is missing (gap)
        List<DailyLog> logs = List.of(
                makeLog(yesterday, true),
                makeLog(yesterday.minusDays(2), true));

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        when(dailyLogRepository.findByHabitIdOrderByLogDateDesc(HABIT_ID)).thenReturn(logs);

        HabitStatsResponse stats = habitService.getHabitStats(HABIT_ID, USER_ID);

        assertEquals(1, stats.currentStreak());
    }

    @Test
    void shouldCalculateLongestStreakAcrossHistory() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // Old 5-day streak, then gap, then current 2-day streak
        List<DailyLog> logs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            logs.add(makeLog(yesterday.minusDays(10 + i), true)); // 5-day streak in past
        }
        logs.add(makeLog(yesterday, true));
        logs.add(makeLog(yesterday.minusDays(1), true)); // 2-day current streak
        logs.sort((a, b) -> b.getLogDate().compareTo(a.getLogDate())); // descending

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        when(dailyLogRepository.findByHabitIdOrderByLogDateDesc(HABIT_ID)).thenReturn(logs);

        HabitStatsResponse stats = habitService.getHabitStats(HABIT_ID, USER_ID);

        assertEquals(5, stats.longestStreak());
        assertEquals(2, stats.currentStreak());
    }

    @Test
    void shouldCalculateCompletionPercentage() {
        // Habit created 10 days ago, 7 completed logs
        Habit habit = new Habit(USER_ID, "Test", null, null);
        setField(habit, "id", HABIT_ID);
        setField(habit, "createdAt", LocalDateTime.now().minusDays(9)); // 10 days ago (today included)

        List<DailyLog> logs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            logs.add(makeLog(LocalDate.now().minusDays(i + 1), true));
        }

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
        when(dailyLogRepository.findByHabitIdOrderByLogDateDesc(HABIT_ID)).thenReturn(logs);

        HabitStatsResponse stats = habitService.getHabitStats(HABIT_ID, USER_ID);

        assertEquals(7, stats.totalCompletedDays());
        assertEquals(70.0, stats.completionPercentage());
    }

    // =========================================================================
    // Heatmap tests
    // =========================================================================

    @Test
    void shouldFillAllDatesInHeatmapRange() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 7);

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        when(dailyLogRepository.findByHabitIdAndLogDateBetweenOrderByLogDateAsc(HABIT_ID, start, end))
                .thenReturn(List.of(makeLog(LocalDate.of(2026, 1, 3), true)));

        HeatmapResponse heatmap = habitService.getHeatmap(HABIT_ID, USER_ID, start, end);

        assertEquals(7, heatmap.days().size());
        assertFalse(heatmap.days().get(0).completed()); // Jan 1 - no log
        assertFalse(heatmap.days().get(1).completed()); // Jan 2 - no log
        assertTrue(heatmap.days().get(2).completed());  // Jan 3 - completed
        assertFalse(heatmap.days().get(3).completed()); // Jan 4 - no log
    }

    @Test
    void shouldIncludeNotesInHeatmapForCompletedDays() {
        LocalDate date = LocalDate.of(2026, 2, 1);
        DailyLog log = makeLog(date, true);
        log.setNotes("Great session!");

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));
        when(dailyLogRepository.findByHabitIdAndLogDateBetweenOrderByLogDateAsc(HABIT_ID, date, date))
                .thenReturn(List.of(log));

        HeatmapResponse heatmap = habitService.getHeatmap(HABIT_ID, USER_ID, date, date);

        assertEquals(1, heatmap.days().size());
        assertEquals("Great session!", heatmap.days().get(0).notes());
    }

    @Test
    void shouldThrowExceptionWhenStartDateAfterEndDate() {
        LocalDate start = LocalDate.of(2026, 2, 28);
        LocalDate end = LocalDate.of(2026, 1, 1);

        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(activeHabit));

        assertThrows(InvalidDateRangeException.class, () ->
                habitService.getHeatmap(HABIT_ID, USER_ID, start, end));
    }

    @Test
    void shouldThrowExceptionWhenGettingHeatmapForNonOwnedHabit() {
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(HabitNotFoundException.class, () ->
                habitService.getHeatmap(HABIT_ID, USER_ID,
                        LocalDate.now().minusDays(7), LocalDate.now()));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private DailyLog makeLog(LocalDate date, boolean completed) {
        DailyLog log = new DailyLog(activeHabit, date, completed, null);
        return log;
    }

    /** Reflectively set a private/no-setter field for test setup. */
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
