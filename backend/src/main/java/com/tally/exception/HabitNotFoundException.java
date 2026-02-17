package com.tally.exception;

public class HabitNotFoundException extends RuntimeException {
    public HabitNotFoundException(Long habitId) {
        super("Habit not found with id: " + habitId);
    }
}
