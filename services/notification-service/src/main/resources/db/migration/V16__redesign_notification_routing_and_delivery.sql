-- Preserve all existing rows and operational state. V1-V15 remain immutable.
-- Rule channels are a collection, not one boolean column per provider.
ALTER TABLE notification_alert_rules ADD COLUMN channels TEXT[];
UPDATE notification_alert_rules SET channels = array_remove(ARRAY[
    CASE WHEN in_app_enabled THEN 'IN_APP' END,
    CASE WHEN email_enabled THEN 'EMAIL' END,
    CASE WHEN telegram_enabled THEN 'TELEGRAM' END,
    CASE WHEN lark_enabled THEN 'LARK' END
], NULL);
ALTER TABLE notification_alert_rules
    ALTER COLUMN channels SET DEFAULT ARRAY['IN_APP']::TEXT[],
    ALTER COLUMN channels SET NOT NULL,
    ADD CONSTRAINT ck_notification_rule_channels CHECK (
        CASE WHEN COALESCE(array_ndims(channels), 1) <> 1 THEN false ELSE
            channels <@ ARRAY['IN_APP','EMAIL','TELEGRAM','LARK']::TEXT[]
            AND array_position(channels, NULL) IS NULL
            AND cardinality(channels) =
                ('IN_APP'=ANY(channels))::INTEGER + ('EMAIL'=ANY(channels))::INTEGER
                + ('TELEGRAM'=ANY(channels))::INTEGER + ('LARK'=ANY(channels))::INTEGER
        END),
    DROP COLUMN in_app_enabled,
    DROP COLUMN email_enabled,
    DROP COLUMN telegram_enabled,
    DROP COLUMN lark_enabled;
-- thresholds/cooldown and all audit fields are preserved, including legacy settings.

-- Optional explicit provider binding, scoped to both tenant and channel.
ALTER TABLE notification_settings
    ADD CONSTRAINT uq_notification_settings_tenant_id_channel UNIQUE (tenant_id, id, channel);

-- In-app read state stays in notification_recipients. This queue is for external channels only.
ALTER TABLE notification_deliveries
    DROP CONSTRAINT notification_deliveries_channel_check,
    ADD CONSTRAINT notification_deliveries_channel_check CHECK (channel IN ('EMAIL','TELEGRAM','LARK')),
    ALTER COLUMN destination TYPE VARCHAR(2048),
    ALTER COLUMN template_code SET DEFAULT 'GENERIC_NOTIFICATION',
    ADD COLUMN setting_id UUID,
    ADD CONSTRAINT fk_notification_delivery_setting FOREIGN KEY (tenant_id, setting_id, channel)
        REFERENCES notification_settings (tenant_id, id, channel);
-- Email identity is case-insensitive; chat IDs and webhook destinations are case-sensitive.
-- Hash the canonical destination to keep index keys bounded even for long webhook URLs.
-- Fixed UTF-8 conversion is deterministic. PostgreSQL marks the generic encoding
-- conversion STABLE, so expose the fixed conversion through an immutable function.
CREATE FUNCTION notification_destination_key(channel TEXT, destination TEXT)
RETURNS TEXT LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT encode(sha256(convert_to(CASE WHEN channel='EMAIL'
        THEN lower(btrim(destination)) ELSE btrim(destination) END, 'UTF8')), 'hex');
$$;
ALTER TABLE notification_deliveries ADD COLUMN destination_key TEXT GENERATED ALWAYS AS (
    notification_destination_key(channel, destination)
) STORED;
ALTER TABLE notification_deliveries ALTER COLUMN destination_key SET NOT NULL;
DROP INDEX uq_notification_delivery_destination;
CREATE UNIQUE INDEX uq_notification_delivery_destination
    ON notification_deliveries (tenant_id, notification_id, channel, destination_key);

-- A cooldown route can identify a user OR an external destination.
-- Existing EMAIL rows without destinations retain their original per-user semantics.
CREATE FUNCTION pg_temp.notification_redesign_uuid_v7(created_at TIMESTAMPTZ)
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

ALTER TABLE notification_deduplication
    ADD COLUMN id UUID,
    ADD COLUMN destination VARCHAR(2048),
    ADD COLUMN setting_id UUID;
UPDATE notification_deduplication SET id = pg_temp.notification_redesign_uuid_v7(last_notified_at);
ALTER TABLE notification_deduplication DROP CONSTRAINT notification_deduplication_pkey;
ALTER TABLE notification_deduplication
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN user_id DROP NOT NULL,
    ADD CONSTRAINT pk_notification_deduplication PRIMARY KEY (id),
    DROP CONSTRAINT notification_deduplication_channel_check,
    ADD CONSTRAINT notification_deduplication_channel_check
        CHECK (channel IN ('IN_APP','EMAIL','TELEGRAM','LARK')),
    ADD CONSTRAINT ck_notification_cooldown_recipient CHECK (
        (channel='IN_APP' AND user_id IS NOT NULL AND destination IS NULL)
        OR (channel<>'IN_APP' AND (user_id IS NOT NULL OR destination IS NOT NULL))
    ),
    ADD CONSTRAINT ck_notification_cooldown_destination CHECK (destination IS NULL OR btrim(destination)<>''),
    ADD CONSTRAINT fk_notification_cooldown_setting FOREIGN KEY (tenant_id, setting_id, channel)
        REFERENCES notification_settings (tenant_id, id, channel);
ALTER TABLE notification_deduplication ADD COLUMN recipient_key TEXT GENERATED ALWAYS AS (
    CASE WHEN destination IS NULL THEN 'USER:' || user_id::TEXT
    ELSE 'DESTINATION:' || notification_destination_key(channel, destination) END
) STORED;
ALTER TABLE notification_deduplication
    ALTER COLUMN recipient_key SET NOT NULL,
    ADD CONSTRAINT uq_notification_cooldown_route
        UNIQUE (tenant_id, type, subject_type, subject_id, channel, recipient_key);
DROP FUNCTION pg_temp.notification_redesign_uuid_v7(TIMESTAMPTZ);
-- New IDs are supplied by the application with UuidV7.nextId(), never by a runtime DB default.

CREATE INDEX idx_notifications_feed
    ON notifications (tenant_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;

COMMENT ON TABLE notification_alert_rules IS
    'Tenant per-type policy and channels array. Legacy thresholds/cooldown and audit data are retained.';
COMMENT ON TABLE notification_recipients IS
    'In-app only: UUIDv7 id, mandatory user_id, independent read_at and archive state per tenant/user.';
COMMENT ON TABLE notification_deliveries IS
    'Durable external delivery queue. user_id is optional; setting_id binds tenant/channel when supplied. Destination is a send-time snapshot.';
COMMENT ON TABLE notification_deduplication IS
    'Cooldown state with UUIDv7 id. In-app keys use users; external keys can use destinations, including shared groups without user_id.';
