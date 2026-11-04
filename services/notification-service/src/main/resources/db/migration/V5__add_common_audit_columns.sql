-- Common business/configuration columns. Application-supplied IDs and composite keys stay unchanged.
-- Timestamps/version on updates remain the responsibility of JPA/application code.

ALTER TABLE notification_features ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE notification_features ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE notification_features SET updated_at = created_at;
ALTER TABLE notification_features
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE notification_features ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;
ALTER TABLE notification_features ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

ALTER TABLE notification_types ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE notification_types ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE notification_types SET updated_at = created_at;
ALTER TABLE notification_types
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE notification_types ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;
ALTER TABLE notification_types ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

ALTER TABLE notification_settings ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE notification_tenant_features ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE notification_alert_rules ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE notification_alert_rule_recipients ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE notification_alert_rule_recipients SET updated_at = created_at;
ALTER TABLE notification_alert_rule_recipients
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE notification_alert_rule_recipients ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;
ALTER TABLE notification_alert_rule_recipients ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

ALTER TABLE notification_user_preferences ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE notifications ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE notifications SET updated_at = created_at;
ALTER TABLE notifications
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE notifications ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;
ALTER TABLE notifications ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

ALTER TABLE notification_recipients ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE notification_recipients SET updated_at = created_at;
ALTER TABLE notification_recipients
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE notification_recipients ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;
ALTER TABLE notification_recipients ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

-- Queue rows remain durable operational state; they are cancelled rather than soft-deleted.
ALTER TABLE notification_deliveries
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

-- Append-only delivery history has a creation timestamp, not mutable audit columns.
ALTER TABLE notification_delivery_attempts ADD COLUMN created_at TIMESTAMPTZ;
UPDATE notification_delivery_attempts SET created_at = finished_at;
ALTER TABLE notification_delivery_attempts
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT now();

-- Identity and idempotency uniqueness intentionally includes soft-deleted rows.
-- Restore/update the same row rather than inserting a duplicate natural key.

DROP INDEX idx_notification_recipient_feed;
CREATE INDEX idx_notification_recipient_feed
    ON notification_recipients (tenant_id, user_id, created_at DESC, notification_id DESC)
    WHERE deleted_at IS NULL;
DROP INDEX idx_notification_recipient_unread;
CREATE INDEX idx_notification_recipient_unread
    ON notification_recipients (tenant_id, user_id, created_at DESC, notification_id DESC)
    WHERE read_at IS NULL AND deleted_at IS NULL;
