ALTER TABLE notification_settings
    DROP CONSTRAINT notification_settings_channel_check;

ALTER TABLE notification_settings
    ADD CONSTRAINT notification_settings_channel_check
        CHECK (channel IN ('IN_APP', 'EMAIL', 'TELEGRAM', 'LARK'));
