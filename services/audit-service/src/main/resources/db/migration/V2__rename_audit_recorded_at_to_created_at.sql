-- Preserve existing audit timestamps while aligning the column name with entity conventions.
ALTER TABLE audit_logs RENAME COLUMN recorded_at TO created_at;
