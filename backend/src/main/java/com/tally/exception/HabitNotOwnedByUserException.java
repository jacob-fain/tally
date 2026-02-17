package com.tally.exception;

public class HabitNotOwnedByUserException extends RuntimeException {
    public HabitNotOwnedByUserException(Long habitId, Long userId) {
        super("Habit with id " + habitId + " is not owned by user " + userId);
    }
}
