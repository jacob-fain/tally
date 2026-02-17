package com.tally.controller;

import com.tally.dto.request.CreateHabitRequest;
import com.tally.dto.request.ReorderHabitsRequest;
import com.tally.dto.request.UpdateHabitRequest;
import com.tally.dto.response.HabitResponse;
import com.tally.dto.response.HabitStatsResponse;
import com.tally.dto.response.HeatmapResponse;
import com.tally.exception.InvalidDateRangeException;
import com.tally.security.CustomUserDetails;
import com.tally.service.HabitService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @GetMapping
    public ResponseEntity<List<HabitResponse>> getAllHabits(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(habitService.getAllHabits(userDetails.getUserId(), includeArchived));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HabitResponse> getHabit(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(habitService.getHabitById(id, userDetails.getUserId()));
    }

    @PostMapping
    public ResponseEntity<HabitResponse> createHabit(
            @Valid @RequestBody CreateHabitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(habitService.createHabit(request, userDetails.getUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HabitResponse> updateHabit(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHabitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(habitService.updateHabit(id, request, userDetails.getUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHabit(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        habitService.deleteHabit(id, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<HabitResponse> archiveHabit(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(habitService.archiveHabit(id, userDetails.getUserId()));
    }

    @PutMapping("/reorder")
    public ResponseEntity<Void> reorderHabits(
            @Valid @RequestBody ReorderHabitsRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        habitService.reorderHabits(request, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<HabitStatsResponse> getHabitStats(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(habitService.getHabitStats(id, userDetails.getUserId()));
    }

    @GetMapping("/{id}/heatmap")
    public ResponseEntity<HeatmapResponse> getHeatmap(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDate start;
        LocalDate end;

        if (startDate != null || endDate != null) {
            if (startDate == null || endDate == null) {
                throw new InvalidDateRangeException("Both startDate and endDate must be provided together");
            }
            start = startDate;
            end = endDate;
        } else if (year != null) {
            start = LocalDate.of(year, 1, 1);
            end = LocalDate.of(year, 12, 31);
        } else if (month != null) {
            try {
                YearMonth ym = YearMonth.parse(month); // YYYY-MM
                start = ym.atDay(1);
                end = ym.atEndOfMonth();
            } catch (Exception e) {
                throw new InvalidDateRangeException("Invalid month format. Expected YYYY-MM (e.g. 2026-02)");
            }
        } else {
            int currentYear = LocalDate.now().getYear();
            start = LocalDate.of(currentYear, 1, 1);
            end = LocalDate.of(currentYear, 12, 31);
        }

        return ResponseEntity.ok(habitService.getHeatmap(id, userDetails.getUserId(), start, end));
    }
}
