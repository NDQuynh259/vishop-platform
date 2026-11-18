-- Bounded maintenance scans have separate clocks: creation for visible rows, deletion for archived rows.
CREATE INDEX idx_notification_recipient_visible_retention
    ON notification_recipients (created_at, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_notification_recipient_archived_retention
    ON notification_recipients (deleted_at, id)
    WHERE deleted_at IS NOT NULL;
