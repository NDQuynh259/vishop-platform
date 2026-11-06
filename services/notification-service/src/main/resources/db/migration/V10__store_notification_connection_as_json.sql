-- Preserve provider references and Telegram metadata, including soft-deleted rows.
ALTER TABLE notification_settings
    ADD COLUMN connection_config JSONB NOT NULL DEFAULT '{}';

UPDATE notification_settings SET connection_config = jsonb_strip_nulls(jsonb_build_object(
    'connectionRef', connection_ref,
    'credentialsRef', credentials_ref,
    'telegramBotUsername', telegram_bot_username,
    'telegramChatId', telegram_chat_id,
    'telegramMessageThreadId', telegram_message_thread_id
));

-- PostgreSQL removes the old column constraints with their columns.
ALTER TABLE notification_settings
    DROP COLUMN connection_ref,
    DROP COLUMN credentials_ref,
    DROP COLUMN telegram_bot_username,
    DROP COLUMN telegram_chat_id,
    DROP COLUMN telegram_message_thread_id,
    ADD CONSTRAINT notification_settings_connection_object_check
        CHECK (jsonb_typeof(connection_config) = 'object'),
    ADD CONSTRAINT notification_settings_connectionref_check
        CHECK (COALESCE(connection_config->>'url', connection_config->>'connectionRef') IS NULL OR
            (jsonb_typeof(COALESCE(connection_config->'url', connection_config->'connectionRef')) = 'string'
             AND btrim(COALESCE(connection_config->>'url', connection_config->>'connectionRef')) <> ''
             AND length(COALESCE(connection_config->>'url', connection_config->>'connectionRef')) <= 200)),
    ADD CONSTRAINT notification_settings_credentialsref_check
        CHECK (COALESCE(connection_config->>'token', connection_config->>'credentialsRef') IS NULL OR
            (jsonb_typeof(COALESCE(connection_config->'token', connection_config->'credentialsRef')) = 'string'
             AND btrim(COALESCE(connection_config->>'token', connection_config->>'credentialsRef')) <> ''
             AND length(COALESCE(connection_config->>'token', connection_config->>'credentialsRef')) <= 300)),
    ADD CONSTRAINT notification_settings_telegrambotusername_check
        CHECK (connection_config->>'telegramBotUsername' IS NULL OR
            (jsonb_typeof(connection_config->'telegramBotUsername') = 'string'
             AND btrim(connection_config->>'telegramBotUsername') <> ''
             AND length(connection_config->>'telegramBotUsername') <= 100)),
    ADD CONSTRAINT notification_settings_telegramchatid_check
        CHECK (COALESCE(connection_config->>'chatId', connection_config->>'telegramChatId') IS NULL OR
            (jsonb_typeof(COALESCE(connection_config->'chatId', connection_config->'telegramChatId')) = 'string'
             AND btrim(COALESCE(connection_config->>'chatId', connection_config->>'telegramChatId')) <> ''
             AND length(COALESCE(connection_config->>'chatId', connection_config->>'telegramChatId')) <= 100)),
    ADD CONSTRAINT notification_settings_telegram_thread_check
        CHECK (CASE
            WHEN connection_config->>'telegramMessageThreadId' IS NULL THEN true
            WHEN jsonb_typeof(connection_config->'telegramMessageThreadId') = 'number'
                AND connection_config->>'telegramMessageThreadId' ~ '^[0-9]+$'
            THEN (connection_config->>'telegramMessageThreadId')::numeric BETWEEN 1 AND 9223372036854775807
            ELSE false END),
    ADD CONSTRAINT notification_settings_telegram_enabled_check
        CHECK (channel <> 'TELEGRAM' OR NOT enabled OR
            (COALESCE(connection_config->>'token', connection_config->>'credentialsRef') IS NOT NULL
             AND COALESCE(connection_config->>'chatId', connection_config->>'telegramChatId') IS NOT NULL)),
    ADD CONSTRAINT notification_settings_telegram_fields_check
        CHECK (channel = 'TELEGRAM' OR
            (connection_config->>'telegramBotUsername' IS NULL
             AND connection_config->>'telegramChatId' IS NULL
             AND connection_config->>'telegramMessageThreadId' IS NULL
             AND connection_config->>'chatId' IS NULL
             AND connection_config->>'token' IS NULL)),
    ADD CONSTRAINT notification_settings_in_app_connection_check
        CHECK (channel <> 'IN_APP' OR
            (COALESCE(connection_config->>'url', connection_config->>'connectionRef') IS NULL
             AND COALESCE(connection_config->>'token', connection_config->>'credentialsRef') IS NULL));

COMMENT ON COLUMN notification_settings.connection_config IS
    'Provider configuration and secret-store references plus Telegram metadata. Never plaintext tokens/passwords.';
COMMENT ON TABLE notification_settings IS
    'Tenant channel connection configuration, unique per tenant/channel. Connection metadata is stored in connection_config JSONB.';
