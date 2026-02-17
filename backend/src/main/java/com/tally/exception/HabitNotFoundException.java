package com.tally.exception;

public class HabitNotFoundException extends RuntimeException {
    public HabitNotFoundException() {
        super("Habit not found");
    }
}
