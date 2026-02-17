package com.tally.dto.response;

import java.time.LocalDate;
import java.util.List;

public record HeatmapResponse(
    LocalDate startDate,
    LocalDate endDate,
    List<HeatmapDayResponse> days
) {}
