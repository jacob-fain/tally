package com.tally.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchDailyLogRequest(
    @NotEmpty(message = "Logs list cannot be empty")
    @Size(max = 100, message = "Cannot process more than 100 logs at once")
    @Valid
    List<CreateDailyLogRequest> logs
) {}
