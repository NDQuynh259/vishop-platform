-- Correct ownership/naming without losing existing preferences or connections.
ALTER TABLE notification_settings RENAME TO notification_user_preferences;
ALTER TABLE notification_tenant_channels RENAME TO notification_settings;

ALTER TABLE notification_settings DROP CONSTRAINT notification_tenant_channels_channel_check;
ALTER TABLE notification_settings ADD CONSTRAINT notification_settings_channel_check
    CHECK (channel IN ('IN_APP', 'EMAIL', 'TELEGRAM'));

ALTER TABLE notification_settings
    ADD COLUMN credentials_ref VARCHAR(300),
    ADD COLUMN telegram_bot_username VARCHAR(100),
    ADD COLUMN telegram_chat_id VARCHAR(100),
    ADD COLUMN telegram_message_thread_id BIGINT,
    ADD CONSTRAINT notification_settings_credentials_ref_check
        CHECK (credentials_ref IS NULL OR btrim(credentials_ref) <> ''),
    ADD CONSTRAINT notification_settings_telegram_username_check
        CHECK (telegram_bot_username IS NULL OR btrim(telegram_bot_username) <> ''),
    ADD CONSTRAINT notification_settings_telegram_chat_check
        CHECK (telegram_chat_id IS NULL OR btrim(telegram_chat_id) <> ''),
    ADD CONSTRAINT notification_settings_telegram_thread_check
        CHECK (telegram_message_thread_id IS NULL OR telegram_message_thread_id > 0),
    ADD CONSTRAINT notification_settings_telegram_enabled_check
        CHECK (channel <> 'TELEGRAM' OR NOT enabled
            OR (credentials_ref IS NOT NULL AND telegram_chat_id IS NOT NULL)),
    ADD CONSTRAINT notification_settings_telegram_fields_check
        CHECK (channel = 'TELEGRAM' OR (telegram_bot_username IS NULL
            AND telegram_chat_id IS NULL AND telegram_message_thread_id IS NULL)),
    ADD CONSTRAINT notification_settings_in_app_credentials_check
        CHECK (channel <> 'IN_APP' OR credentials_ref IS NULL);

ALTER TABLE notification_types ADD COLUMN default_telegram_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE notification_alert_rules ADD COLUMN telegram_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE notification_user_preferences ADD COLUMN telegram_enabled BOOLEAN NOT NULL DEFAULT true;

COMMENT ON TABLE notification_settings IS
    'Tenant channel connection configuration, unique per tenant/channel. Telegram bot token is resolved using credentials_ref, never stored as plaintext.';
COMMENT ON COLUMN notification_settings.credentials_ref IS
    'Opaque reference to secrets storage: Telegram bot token or email provider credentials. Never the token/password itself.';
COMMENT ON COLUMN notification_settings.telegram_chat_id IS
    'Telegram destination chat/group/channel ID; not an IAM user ID. May be a negative ID.';
COMMENT ON TABLE notification_user_preferences IS
    'Personal per-tenant/user/type delivery preferences; does not hold bot or provider connection information.';
