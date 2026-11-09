-- A tenant may configure multiple Lark group webhooks, each addressed by its setting ID.
-- Keep the tenant/id/channel unique key used by delivery foreign keys.
ALTER TABLE notification_settings DROP CONSTRAINT uq_notification_settings_tenant_channel;
CREATE UNIQUE INDEX uq_notification_settings_single_channel
    ON notification_settings (tenant_id, channel) WHERE channel <> 'LARK';
CREATE INDEX idx_notification_settings_tenant_channel_id
    ON notification_settings (tenant_id, channel, id) WHERE deleted_at IS NULL;
