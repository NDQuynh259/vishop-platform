ALTER TABLE notification_settings ADD COLUMN name varchar(150);
UPDATE notification_settings SET name = channel WHERE name IS NULL;
ALTER TABLE notification_settings ALTER COLUMN name SET NOT NULL;
ALTER TABLE notification_settings ALTER COLUMN name SET DEFAULT 'Notification';
ALTER TABLE notification_settings ADD CONSTRAINT ck_notification_setting_name
    CHECK (length(btrim(name)) > 0);
