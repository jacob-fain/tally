package com.tally.repository;

import com.tally.model.DailyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyLogRepository extends JpaRepository<DailyLog, Long> {

    Optional<DailyLog> findByHabitIdAndLogDate(Long habitId, LocalDate logDate);

    List<DailyLog> findByHabitIdAndLogDateBetweenOrderByLogDateAsc(
            Long habitId, LocalDate startDate, LocalDate endDate);

    List<DailyLog> findByHabitIdOrderByLogDateDesc(Long habitId);

    // Custom JPQL: verify ownership through the habit relationship
    // DailyLog doesn't have userId directly, so we join through habit
    @Query("SELECT dl FROM DailyLog dl WHERE dl.id = :logId AND dl.habit.userId = :userId")
    Optional<DailyLog> findByIdAndHabitUserId(@Param("logId") Long logId, @Param("userId") Long userId);
}
