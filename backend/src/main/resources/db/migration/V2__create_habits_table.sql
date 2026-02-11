-- Create habits table
-- Stores user-defined habits to track

CREATE TABLE habits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    color VARCHAR(7),  -- Hex color for UI (e.g. #FF5733)
    display_order INTEGER DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_habits_user_id ON habits(user_id);
CREATE INDEX idx_habits_user_archived ON habits(user_id, archived);

-- Comments for documentation
COMMENT ON TABLE habits IS 'User-defined habits to track daily';
COMMENT ON COLUMN habits.user_id IS 'Foreign key to users table (cascading delete)';
COMMENT ON COLUMN habits.name IS 'Habit name (e.g. "No Soda", "Workout")';
COMMENT ON COLUMN habits.description IS 'Optional detailed description';
COMMENT ON COLUMN habits.color IS 'Hex color code for UI heatmap visualization';
COMMENT ON COLUMN habits.display_order IS 'User-defined sort order for displaying habits';
COMMENT ON COLUMN habits.archived IS 'Soft delete flag - archived habits are hidden but preserved';
COMMENT ON COLUMN habits.archived_at IS 'Timestamp when habit was archived';
