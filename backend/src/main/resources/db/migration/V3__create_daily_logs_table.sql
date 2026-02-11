-- Create daily_logs table
-- Stores daily completion status for each habit

CREATE TABLE daily_logs (
    id BIGSERIAL PRIMARY KEY,
    habit_id BIGINT NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    completed BOOLEAN NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_habit_date UNIQUE(habit_id, log_date)
);

-- Indexes for performance
CREATE INDEX idx_daily_logs_habit_id ON daily_logs(habit_id);
CREATE INDEX idx_daily_logs_date ON daily_logs(log_date);
CREATE INDEX idx_daily_logs_habit_date_range ON daily_logs(habit_id, log_date);

-- Comments for documentation
COMMENT ON TABLE daily_logs IS 'Daily completion records for habits';
COMMENT ON COLUMN daily_logs.habit_id IS 'Foreign key to habits table (cascading delete)';
COMMENT ON COLUMN daily_logs.log_date IS 'Date of the log entry (e.g. 2026-02-11)';
COMMENT ON COLUMN daily_logs.completed IS 'Whether habit was completed on this date';
COMMENT ON COLUMN daily_logs.notes IS 'Optional notes about the day';
COMMENT ON COLUMN daily_logs.created_at IS 'When this log entry was first created';
COMMENT ON COLUMN daily_logs.updated_at IS 'Last time this log was modified';
COMMENT ON CONSTRAINT unique_habit_date ON daily_logs IS 'Ensures only one log per habit per date';
