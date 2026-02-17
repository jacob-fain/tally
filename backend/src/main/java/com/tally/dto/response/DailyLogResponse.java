package com.tally.dto.response;

import com.tally.model.DailyLog;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyLogResponse(
    Long id,
    Long habitId,
    LocalDate logDate,
    Boolean completed,
    String notes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static DailyLogResponse fromEntity(DailyLog log) {
        return new DailyLogResponse(
            log.getId(),
            log.getHabit().getId(),
            log.getLogDate(),
            log.getCompleted(),
            log.getNotes(),
            log.getCreatedAt(),
            log.getUpdatedAt()
        );
    }
}
