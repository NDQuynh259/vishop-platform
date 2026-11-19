-- Retention uses a single creation-time clock for both visible and soft-deleted recipients.
DROP INDEX idx_notification_recipient_visible_retention;
DROP INDEX idx_notification_recipient_archived_retention;

CREATE INDEX idx_notification_recipient_retention
    ON notification_recipients (created_at, id);
