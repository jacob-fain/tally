package com.tally.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderHabitsRequest(
    @NotEmpty(message = "Habit order list cannot be empty")
    @Valid
    List<HabitOrderItem> habitOrders
) {
    public record HabitOrderItem(
        @NotNull(message = "Habit ID is required")
        Long habitId,

        @NotNull(message = "Display order is required")
        @Min(value = 0, message = "Display order must be non-negative")
        Integer displayOrder
    ) {}
}
