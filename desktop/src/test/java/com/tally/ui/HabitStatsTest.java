package com.tally.ui;

import com.tally.model.DailyLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the streak and completion percentage calculations.
 *
 * HabitRow's calculateStreak() and calculateCompletionPct() are private,
 * so we extract the equivalent logic here as package-private static helpers
 * and test them in isolation — no JavaFX toolkit required.
 *
 * The logic mirrors HabitRow exactly so any change there should be reflected here.
 */
class HabitStatsTest {

    // =========================================================================
    // Replicated logic (mirrors HabitRow private methods)
    // =========================================================================

    /** Count consecutive completed days ending at today (backwards). */
    static int calculateStreak(List<DailyLog> logs) {
        LocalDate date = LocalDate.now();
        int streak = 0;
        while (true) {
            final LocalDate d = date;
            boolean completed = logs.stream()
                    .anyMatch(l -> l.getLogDate().equals(d) && l.isCompleted());
            if (!completed) break;
            streak++;
            date = date.minusDays(1);
        }
        return streak;
    }

    /** Completion % for the given year up to today. */
    static double calculateCompletionPct(List<DailyLog> logs, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate today = LocalDate.now();
        long daysSoFar = today.toEpochDay() - yearStart.toEpochDay() + 1;
        if (daysSoFar <= 0) return 0;

        long completed = logs.stream()
                .filter(DailyLog::isCompleted)
                .filter(l -> !l.getLogDate().isAfter(today))
                .count();

        return (completed * 100.0) / daysSoFar;
    }

    // =========================================================================
    // Streak tests
    // =========================================================================

    @Test
    void streakShouldBeZeroWithNoLogs() {
        assertEquals(0, calculateStreak(List.of()));
    }

    @Test
    void streakShouldBeZeroWhenTodayNotCompleted() {
        List<DailyLog> logs = new ArrayList<>();
        // Yesterday completed, today not
        logs.add(log(LocalDate.now().minusDays(1), true));
        assertEquals(0, calculateStreak(logs));
    }

    @Test
    void streakShouldCountTodayOnly() {
        List<DailyLog> logs = new ArrayList<>();
        logs.add(log(LocalDate.now(), true));
        assertEquals(1, calculateStreak(logs));
    }

    @Test
    void streakShouldCountConsecutiveDaysBackFromToday() {
        List<DailyLog> logs = new ArrayList<>();
        logs.add(log(LocalDate.now(), true));
        logs.add(log(LocalDate.now().minusDays(1), true));
        logs.add(log(LocalDate.now().minusDays(2), true));
        assertEquals(3, calculateStreak(logs));
    }

    @Test
    void streakShouldStopAtGap() {
        List<DailyLog> logs = new ArrayList<>();
        logs.add(log(LocalDate.now(), true));
        logs.add(log(LocalDate.now().minusDays(1), true));
        // Gap on day -2
        logs.add(log(LocalDate.now().minusDays(3), true));
        assertEquals(2, calculateStreak(logs));
    }

    @Test
    void streakShouldIgnoreIncompletedDays() {
        List<DailyLog> logs = new ArrayList<>();
        logs.add(log(LocalDate.now(), true));
        logs.add(log(LocalDate.now().minusDays(1), false)); // not completed = breaks streak
        logs.add(log(LocalDate.now().minusDays(2), true));
        assertEquals(1, calculateStreak(logs));
    }

    // =========================================================================
    // Completion percentage tests
    // =========================================================================

    @Test
    void completionPctShouldBeZeroWithNoLogs() {
        double pct = calculateCompletionPct(List.of(), LocalDate.now().getYear());
        assertEquals(0.0, pct, 0.01);
    }

    @Test
    void completionPctShouldExcludeFutureDates() {
        int year = LocalDate.now().getYear();
        List<DailyLog> logs = new ArrayList<>();
        // A completed log for a future date — should not count
        logs.add(log(LocalDate.now().plusDays(10), true));
        double pct = calculateCompletionPct(logs, year);
        assertEquals(0.0, pct, 0.01);
    }

    @Test
    void completionPctShouldCountOnlyCompletedPastDays() {
        int year = LocalDate.now().getYear();
        LocalDate today = LocalDate.now();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        long daysSoFar = today.toEpochDay() - yearStart.toEpochDay() + 1;

        List<DailyLog> logs = new ArrayList<>();
        logs.add(log(today, true));
        logs.add(log(today.minusDays(1), true));

        double pct = calculateCompletionPct(logs, year);
        double expected = (2.0 / daysSoFar) * 100.0;
        assertEquals(expected, pct, 0.01);
    }

    @Test
    void completionPctShouldIgnoreIncompleteLogs() {
        int year = LocalDate.now().getYear();
        List<DailyLog> logs = new ArrayList<>();
        logs.add(log(LocalDate.now(), false)); // incomplete — should not count
        double pct = calculateCompletionPct(logs, year);
        assertEquals(0.0, pct, 0.01);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private DailyLog log(LocalDate date, boolean completed) {
        DailyLog log = new DailyLog();
        log.setLogDate(date);
        log.setCompleted(completed);
        return log;
    }
}
