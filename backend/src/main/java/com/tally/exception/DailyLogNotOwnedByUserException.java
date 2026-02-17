package com.tally.exception;

public class DailyLogNotOwnedByUserException extends RuntimeException {
    public DailyLogNotOwnedByUserException(Long logId, Long userId) {
        super("Daily log with id " + logId + " is not owned by user " + userId);
    }
}
