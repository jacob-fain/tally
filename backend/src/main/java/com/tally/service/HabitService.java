package com.tally.service;

import com.tally.dto.request.CreateHabitRequest;
import com.tally.dto.request.ReorderHabitsRequest;
import com.tally.dto.request.UpdateHabitRequest;
import com.tally.dto.response.HabitResponse;
import com.tally.dto.response.HabitStatsResponse;
import com.tally.dto.response.HeatmapDayResponse;
import com.tally.dto.response.HeatmapResponse;
import com.tally.exception.HabitNotFoundException;
import com.tally.exception.InvalidDateRangeException;
import com.tally.model.DailyLog;
import com.tally.model.Habit;
import com.tally.repository.DailyLogRepository;
import com.tally.repository.HabitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final DailyLogRepository dailyLogRepository;

    public HabitService(HabitRepository habitRepository, DailyLogRepository dailyLogRepository) {
        this.habitRepository = habitRepository;
        this.dailyLogRepository = dailyLogRepository;
    }

    // =========================================================================
    // CRUD Operations
    // =========================================================================

    @Transactional(readOnly = true)
    public List<HabitResponse> getAllHabits(Long userId, boolean includeArchived) {
        List<Habit> habits = includeArchived
                ? habitRepository.findByUserIdOrderByDisplayOrderAsc(userId)
                : habitRepository.findByUserIdAndArchivedFalseOrderByDisplayOrderAsc(userId);
        return habits.stream()
                .map(HabitResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public HabitResponse getHabitById(Long habitId, Long userId) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException());
        return HabitResponse.fromEntity(habit);
    }

    @Transactional
    public HabitResponse createHabit(CreateHabitRequest request, Long userId) {
        Habit habit = new Habit(userId, request.name(), request.description(), request.color());
        Habit saved = habitRepository.save(habit);
        return HabitResponse.fromEntity(saved);
    }

    @Transactional
    public HabitResponse updateHabit(Long habitId, UpdateHabitRequest request, Long userId) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException());
        habit.setName(request.name());
        habit.setDescription(request.description());
        habit.setColor(request.color());
        return HabitResponse.fromEntity(habitRepository.save(habit));
    }

    @Transactional
    public void deleteHabit(Long habitId, Long userId) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException());
        habitRepository.delete(habit);
    }

    @Transactional
    public HabitResponse archiveHabit(Long habitId, Long userId) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException());
        habit.setArchived(true);
        habit.setArchivedAt(LocalDateTime.now());
        return HabitResponse.fromEntity(habitRepository.save(habit));
    }

    @Transactional
    public void reorderHabits(ReorderHabitsRequest request, Long userId) {
        for (ReorderHabitsRequest.HabitOrderItem item : request.habitOrders()) {
            Habit habit = habitRepository.findByIdAndUserId(item.habitId(), userId)
                    .orElseThrow(() -> new HabitNotFoundException());
            habit.setDisplayOrder(item.displayOrder());
            habitRepository.save(habit);
        }
    }

    // =========================================================================
    // Stats & Heatmap
    // =========================================================================

    @Transactional(readOnly = true)
    public HabitStatsResponse getHabitStats(Long habitId, Long userId) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException());

        List<DailyLog> allLogs = dailyLogRepository.findByHabitIdOrderByLogDateDesc(habitId);

        int currentStreak = calculateCurrentStreak(allLogs);
        int longestStreak = calculateLongestStreak(allLogs);
        int totalCompleted = (int) allLogs.stream().filter(DailyLog::getCompleted).count();
        double completionPercentage = calculateCompletionPercentage(habit.getCreatedAt(), totalCompleted);

        return new HabitStatsResponse(habitId, currentStreak, longestStreak, totalCompleted, completionPercentage);
    }

    @Transactional(readOnly = true)
    public HeatmapResponse getHeatmap(Long habitId, Long userId, LocalDate startDate, LocalDate endDate) {
        habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException());

        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("Start date must be before or equal to end date");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 365) {
            throw new InvalidDateRangeException("Date range cannot exceed 366 days");
        }

        List<DailyLog> logs = dailyLogRepository
                .findByHabitIdAndLogDateBetweenOrderByLogDateAsc(habitId, startDate, endDate);

        // Map for O(1) lookup by date
        Map<LocalDate, DailyLog> logMap = logs.stream()
                .collect(Collectors.toMap(DailyLog::getLogDate, log -> log));

        // Fill every date in range, missing dates become completed=false
        List<HeatmapDayResponse> days = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            DailyLog log = logMap.get(current);
            days.add(new HeatmapDayResponse(
                    current,
                    log != null ? log.getCompleted() : false,
                    log != null ? log.getNotes() : null));
            current = current.plusDays(1);
        }

        return new HeatmapResponse(startDate, endDate, days);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Current streak: consecutive completed days ending with today or yesterday.
     * If today is completed, streak includes today. Otherwise, falls back to yesterday.
     * Miss any day = streak resets to 0.
     */
    private int calculateCurrentStreak(List<DailyLog> logsDescending) {
        if (logsDescending.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Determine anchor: today if completed, otherwise yesterday
        boolean todayCompleted = logsDescending.stream()
                .anyMatch(log -> log.getLogDate().equals(today) && log.getCompleted());
        LocalDate anchor = todayCompleted ? today : yesterday;

        // Verify anchor day is completed
        DailyLog anchorLog = logsDescending.stream()
                .filter(log -> log.getLogDate().equals(anchor))
                .findFirst()
                .orElse(null);

        if (anchorLog == null || !anchorLog.getCompleted()) {
            return 0;
        }

        // Count consecutive completed days backwards from anchor
        int streak = 0;
        LocalDate expectedDate = anchor;

        for (DailyLog log : logsDescending) {
            if (log.getLogDate().isAfter(expectedDate)) {
                continue; // Skip any logs newer than anchor
            }
            if (log.getLogDate().equals(expectedDate) && log.getCompleted()) {
                streak++;
                expectedDate = expectedDate.minusDays(1);
            } else {
                break; // Gap or incomplete day found
            }
        }

        return streak;
    }

    /**
     * Longest streak: the longest consecutive sequence of completed days in all history.
     */
    private int calculateLongestStreak(List<DailyLog> logsDescending) {
        if (logsDescending.isEmpty()) {
            return 0;
        }

        // Sort ascending for easier forward traversal
        List<DailyLog> logsAscending = new ArrayList<>(logsDescending);
        Collections.reverse(logsAscending);

        int maxStreak = 0;
        int currentStreak = 0;
        LocalDate expectedDate = null;

        for (DailyLog log : logsAscending) {
            if (!log.getCompleted()) {
                currentStreak = 0;
                expectedDate = null;
                continue;
            }

            if (expectedDate == null || log.getLogDate().equals(expectedDate)) {
                currentStreak++;
                maxStreak = Math.max(maxStreak, currentStreak);
                expectedDate = log.getLogDate().plusDays(1);
            } else {
                // Gap found - restart streak from this day
                currentStreak = 1;
                maxStreak = Math.max(maxStreak, currentStreak);
                expectedDate = log.getLogDate().plusDays(1);
            }
        }

        return maxStreak;
    }

    /**
     * Completion %: total completed days / days since habit was created * 100.
     */
    private double calculateCompletionPercentage(LocalDateTime createdAt, int totalCompleted) {
        LocalDate startDate = createdAt.toLocalDate();
        LocalDate today = LocalDate.now();
        long daysSinceCreation = ChronoUnit.DAYS.between(startDate, today) + 1;

        if (daysSinceCreation <= 0) {
            return 0.0;
        }

        double percentage = Math.round((totalCompleted / (double) daysSinceCreation) * 10000.0) / 100.0;
        return Math.min(100.0, percentage);
    }
}
