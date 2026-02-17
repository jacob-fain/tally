package com.tally.exception;

public class DailyLogNotFoundException extends RuntimeException {
    public DailyLogNotFoundException(Long logId) {
        super("Daily log not found");
    }
}
