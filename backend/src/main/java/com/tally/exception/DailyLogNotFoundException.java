package com.tally.exception;

public class DailyLogNotFoundException extends RuntimeException {
    public DailyLogNotFoundException() {
        super("Daily log not found");
    }
}
