package com.tally.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Mirrors the backend DailyLog entity / DailyLogResponse DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyLog {

    private Long id;
    private Long habitId;
    private LocalDate logDate;
    private boolean completed;
    private String notes;

    public DailyLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getHabitId() { return habitId; }
    public void setHabitId(Long habitId) { this.habitId = habitId; }

    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
