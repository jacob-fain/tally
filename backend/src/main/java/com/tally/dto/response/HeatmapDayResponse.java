package com.tally.dto.response;

import java.time.LocalDate;

public record HeatmapDayResponse(
    LocalDate date,
    Boolean completed,
    String notes
) {}
