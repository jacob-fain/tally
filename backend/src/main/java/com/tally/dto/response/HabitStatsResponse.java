package com.tally.dto.response;

public record HabitStatsResponse(
    Long habitId,
    Integer currentStreak,
    Integer longestStreak,
    Integer totalCompletedDays,
    Double completionPercentage
) {}
