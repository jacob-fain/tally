package com.tally.repository;

import com.tally.model.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HabitRepository extends JpaRepository<Habit, Long> {

    List<Habit> findByUserIdAndArchivedFalseOrderByDisplayOrderAsc(Long userId);

    List<Habit> findByUserIdOrderByDisplayOrderAsc(Long userId);

    Optional<Habit> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}
