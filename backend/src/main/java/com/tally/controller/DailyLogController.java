package com.tally.controller;

import com.tally.dto.request.BatchDailyLogRequest;
import com.tally.dto.request.CreateDailyLogRequest;
import com.tally.dto.response.DailyLogResponse;
import com.tally.security.CustomUserDetails;
import com.tally.service.DailyLogService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class DailyLogController {

    private final DailyLogService dailyLogService;

    public DailyLogController(DailyLogService dailyLogService) {
        this.dailyLogService = dailyLogService;
    }

    @GetMapping
    public ResponseEntity<List<DailyLogResponse>> getLogs(
            @RequestParam Long habitId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                dailyLogService.getLogsByDateRange(habitId, startDate, endDate, userDetails.getUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DailyLogResponse> getLog(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(dailyLogService.getLogById(id, userDetails.getUserId()));
    }

    @PostMapping
    public ResponseEntity<DailyLogResponse> createOrUpdateLog(
            @Valid @RequestBody CreateDailyLogRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dailyLogService.createOrUpdateLog(request, userDetails.getUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLog(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        dailyLogService.deleteLog(id, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch")
    public ResponseEntity<List<DailyLogResponse>> batchCreateOrUpdateLogs(
            @Valid @RequestBody BatchDailyLogRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dailyLogService.batchCreateOrUpdateLogs(request, userDetails.getUserId()));
    }
}
