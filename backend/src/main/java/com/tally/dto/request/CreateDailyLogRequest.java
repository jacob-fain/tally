package com.tally.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateDailyLogRequest(
    @NotNull(message = "Habit ID is required")
    Long habitId,

    @NotNull(message = "Log date is required")
    @PastOrPresent(message = "Log date cannot be in the future")
    LocalDate logDate,

    @NotNull(message = "Completed status is required")
    Boolean completed,

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    String notes
) {}
