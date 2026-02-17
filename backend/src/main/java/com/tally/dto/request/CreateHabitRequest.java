package com.tally.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateHabitRequest(
    @NotBlank(message = "Habit name is required")
    @Size(max = 100, message = "Habit name must not exceed 100 characters")
    String name,

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    String description,

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex code (e.g., #3498db)")
    String color
) {}
