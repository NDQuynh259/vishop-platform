DROP INDEX uq_notification_settings_single_channel;
CREATE UNIQUE INDEX uq_notification_settings_single_channel
    ON notification_settings (tenant_id, channel)
    WHERE channel NOT IN ('TELEGRAM', 'LARK');
