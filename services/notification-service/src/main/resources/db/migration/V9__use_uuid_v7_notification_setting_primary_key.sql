-- Backfill existing channel settings without changing their tenant, code or audit state.
-- PostgreSQL 16: migration-only UUIDv7 helper, same layout as common-domain UuidV7.
CREATE FUNCTION pg_temp.notification_channel_setting_uuid_v7(created_at TIMESTAMPTZ)
RETURNS UUID LANGUAGE plpgsql VOLATILE STRICT AS $$
DECLARE
    epoch_millis BIGINT;
    bytes BYTEA;
BEGIN
    epoch_millis := floor(extract(epoch FROM created_at) * 1000)::BIGINT;
    bytes := decode(lpad(to_hex(epoch_millis & 281474976710655), 12, '0'), 'hex')
        || substring(uuid_send(gen_random_uuid()) FROM 7 FOR 10);
    bytes := set_byte(bytes, 6, (get_byte(bytes, 6) & 15) | 112);
    bytes := set_byte(bytes, 8, (get_byte(bytes, 8) & 63) | 128);
    RETURN encode(bytes, 'hex')::UUID;
END;
$$;

ALTER TABLE notification_settings ADD COLUMN id UUID;
UPDATE notification_settings SET id = pg_temp.notification_channel_setting_uuid_v7(created_at);
ALTER TABLE notification_settings
    ALTER COLUMN id SET NOT NULL,
    DROP CONSTRAINT notification_tenant_channels_pkey,
    ADD CONSTRAINT pk_notification_channel_settings PRIMARY KEY (id),
    ADD CONSTRAINT uq_notification_settings_tenant_channel UNIQUE (tenant_id, channel);

DROP FUNCTION pg_temp.notification_channel_setting_uuid_v7(TIMESTAMPTZ);
-- No runtime DEFAULT: application creation supplies UuidV7.nextId().
