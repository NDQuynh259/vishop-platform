-- Preserve business codes and their foreign-key contracts; use UUIDv7 surrogate PKs.
-- PostgreSQL 16 has no native uuidv7(). This migration-only helper mirrors the
-- timestamp/version/variant layout of common-domain UuidV7.fromTimestamp().
CREATE FUNCTION pg_temp.notification_catalog_uuid_v7(created_at TIMESTAMPTZ)
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

ALTER TABLE notification_features ADD COLUMN id UUID;
UPDATE notification_features SET id = pg_temp.notification_catalog_uuid_v7(created_at);
ALTER TABLE notification_features ALTER COLUMN id SET NOT NULL;

ALTER TABLE notification_types ADD COLUMN id UUID;
UPDATE notification_types SET id = pg_temp.notification_catalog_uuid_v7(created_at);
ALTER TABLE notification_types ALTER COLUMN id SET NOT NULL;

-- Existing FK dependencies must be recreated against the new unique code keys.
ALTER TABLE notification_types DROP CONSTRAINT notification_types_feature_code_fkey;
ALTER TABLE notification_tenant_features DROP CONSTRAINT notification_tenant_features_feature_code_fkey;
ALTER TABLE notifications DROP CONSTRAINT fk_notifications_type;
ALTER TABLE notification_alert_rules DROP CONSTRAINT fk_notification_alert_rule_type;
ALTER TABLE notification_user_preferences DROP CONSTRAINT fk_notification_setting_type;
ALTER TABLE notification_deduplication DROP CONSTRAINT fk_notification_deduplication_type;

ALTER TABLE notification_features
    DROP CONSTRAINT notification_features_pkey,
    ADD CONSTRAINT notification_features_pkey PRIMARY KEY (id),
    ADD CONSTRAINT uq_notification_features_code UNIQUE (code);
ALTER TABLE notification_types
    DROP CONSTRAINT notification_types_pkey,
    ADD CONSTRAINT notification_types_pkey PRIMARY KEY (id),
    ADD CONSTRAINT uq_notification_types_code UNIQUE (code);

ALTER TABLE notification_types ADD CONSTRAINT notification_types_feature_code_fkey
    FOREIGN KEY (feature_code) REFERENCES notification_features (code);
ALTER TABLE notification_tenant_features ADD CONSTRAINT notification_tenant_features_feature_code_fkey
    FOREIGN KEY (feature_code) REFERENCES notification_features (code);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_type
    FOREIGN KEY (type) REFERENCES notification_types (code);
ALTER TABLE notification_alert_rules ADD CONSTRAINT fk_notification_alert_rule_type
    FOREIGN KEY (type) REFERENCES notification_types (code);
ALTER TABLE notification_user_preferences ADD CONSTRAINT fk_notification_setting_type
    FOREIGN KEY (type) REFERENCES notification_types (code);
ALTER TABLE notification_deduplication ADD CONSTRAINT fk_notification_deduplication_type
    FOREIGN KEY (type) REFERENCES notification_types (code);

DROP FUNCTION pg_temp.notification_catalog_uuid_v7(TIMESTAMPTZ);
-- No runtime DEFAULT or GeneratedValue: application creation uses UuidV7.nextId().
