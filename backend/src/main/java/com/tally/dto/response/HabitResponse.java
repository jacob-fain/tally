package com.tally.dto.response;

import com.tally.model.Habit;

import java.time.LocalDateTime;

public record HabitResponse(
    Long id,
    String name,
    String description,
    String color,
    LocalDateTime createdAt,
    Boolean archived,
    LocalDateTime archivedAt,
    Integer displayOrder
) {
    public static HabitResponse fromEntity(Habit habit) {
        return new HabitResponse(
            habit.getId(),
            habit.getName(),
            habit.getDescription(),
            habit.getColor(),
            habit.getCreatedAt(),
            habit.getArchived(),
            habit.getArchivedAt(),
            habit.getDisplayOrder()
        );
    }
}
