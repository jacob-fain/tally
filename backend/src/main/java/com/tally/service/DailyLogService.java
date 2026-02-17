package com.tally.service;

import com.tally.dto.request.BatchDailyLogRequest;
import com.tally.dto.request.CreateDailyLogRequest;
import com.tally.dto.response.DailyLogResponse;
import com.tally.exception.DailyLogNotFoundException;
import com.tally.exception.HabitNotFoundException;
import com.tally.exception.InvalidDateRangeException;
import com.tally.model.DailyLog;
import com.tally.model.Habit;
import com.tally.repository.DailyLogRepository;
import com.tally.repository.HabitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DailyLogService {

    private final DailyLogRepository dailyLogRepository;
    private final HabitRepository habitRepository;

    public DailyLogService(DailyLogRepository dailyLogRepository, HabitRepository habitRepository) {
        this.dailyLogRepository = dailyLogRepository;
        this.habitRepository = habitRepository;
    }

    @Transactional(readOnly = true)
    public List<DailyLogResponse> getLogsByDateRange(
            Long habitId, LocalDate startDate, LocalDate endDate, Long userId) {
        if (!habitRepository.existsByIdAndUserId(habitId, userId)) {
            throw new HabitNotFoundException(habitId);
        }
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("Start date must be before or equal to end date");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > 366) {
            throw new InvalidDateRangeException("Date range cannot exceed 366 days");
        }
        return dailyLogRepository
                .findByHabitIdAndLogDateBetweenOrderByLogDateAsc(habitId, startDate, endDate)
                .stream()
                .map(DailyLogResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public DailyLogResponse getLogById(Long logId, Long userId) {
        DailyLog log = dailyLogRepository.findByIdAndHabitUserId(logId, userId)
                .orElseThrow(() -> new DailyLogNotFoundException(logId));
        return DailyLogResponse.fromEntity(log);
    }

    /**
     * Upsert: if a log already exists for (habitId, logDate), update it.
     * Otherwise create a new one.
     * This simplifies frontend sync - always POST, never worry about PUT vs POST.
     */
    @Transactional
    public DailyLogResponse createOrUpdateLog(CreateDailyLogRequest request, Long userId) {
        Habit habit = habitRepository.findByIdAndUserId(request.habitId(), userId)
                .orElseThrow(() -> new HabitNotFoundException(request.habitId()));

        Optional<DailyLog> existing =
                dailyLogRepository.findByHabitIdAndLogDate(request.habitId(), request.logDate());

        DailyLog log;
        if (existing.isPresent()) {
            log = existing.get();
            log.setCompleted(request.completed());
            log.setNotes(request.notes());
        } else {
            log = new DailyLog(habit, request.logDate(), request.completed(), request.notes());
        }

        return DailyLogResponse.fromEntity(dailyLogRepository.save(log));
    }

    @Transactional
    public void deleteLog(Long logId, Long userId) {
        DailyLog log = dailyLogRepository.findByIdAndHabitUserId(logId, userId)
                .orElseThrow(() -> new DailyLogNotFoundException(logId));
        dailyLogRepository.delete(log);
    }

    @Transactional
    public List<DailyLogResponse> batchCreateOrUpdateLogs(BatchDailyLogRequest request, Long userId) {
        List<DailyLogResponse> responses = new ArrayList<>();
        for (CreateDailyLogRequest logRequest : request.logs()) {
            responses.add(createOrUpdateLog(logRequest, userId));
        }
        return responses;
    }
}
