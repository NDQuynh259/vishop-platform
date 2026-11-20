CREATE TABLE notification_setting_types (
    id UUID PRIMARY KEY,
    setting_id UUID NOT NULL REFERENCES notification_settings(id) ON DELETE CASCADE,
    rule_id UUID NOT NULL REFERENCES notification_alert_rules(id) ON DELETE CASCADE,
    UNIQUE (setting_id, rule_id)
);
CREATE INDEX idx_notification_setting_types_rule ON notification_setting_types(rule_id);

CREATE FUNCTION validate_notification_setting_type() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM notification_settings s
        JOIN notification_alert_rules r ON r.tenant_id = s.tenant_id
        WHERE s.id = NEW.setting_id AND r.id = NEW.rule_id AND s.channel IN ('TELEGRAM','LARK')
        FOR SHARE OF s,r
    ) THEN
        RAISE EXCEPTION 'Group and rule must belong to the same tenant' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER validate_notification_setting_type BEFORE INSERT OR UPDATE OF setting_id,rule_id
    ON notification_setting_types FOR EACH ROW EXECUTE FUNCTION validate_notification_setting_type();

-- Tenant and channel are immutable ownership keys in the application as well.
CREATE FUNCTION reject_notification_owner_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Notification ownership is immutable' USING ERRCODE='23514';
END
$$;
CREATE TRIGGER keep_notification_setting_owner BEFORE UPDATE OF tenant_id,channel ON notification_settings
    FOR EACH ROW WHEN (OLD.tenant_id <> NEW.tenant_id OR OLD.channel <> NEW.channel)
    EXECUTE FUNCTION reject_notification_owner_change();
CREATE TRIGGER keep_notification_rule_owner BEFORE UPDATE OF tenant_id ON notification_alert_rules
    FOR EACH ROW WHEN (OLD.tenant_id <> NEW.tenant_id) EXECUTE FUNCTION reject_notification_owner_change();

CREATE FUNCTION pg_temp.setting_type_uuid_v7(created_at TIMESTAMPTZ)
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

INSERT INTO notification_alert_rules(id,tenant_id,type)
SELECT pg_temp.setting_type_uuid_v7(now()), tenant_id, type
FROM (SELECT DISTINCT tenant_id, unnest(allowed_types) AS type FROM notification_settings) wanted
ON CONFLICT (tenant_id,type) DO NOTHING;

INSERT INTO notification_setting_types(id,setting_id,rule_id)
SELECT pg_temp.setting_type_uuid_v7(now()), s.id, r.id
FROM notification_settings s JOIN notification_alert_rules r
  ON r.tenant_id=s.tenant_id AND r.type=ANY(s.allowed_types);

DO $$
BEGIN
    IF EXISTS (
        SELECT s.id FROM notification_settings s LEFT JOIN notification_setting_types l ON l.setting_id=s.id
        GROUP BY s.id HAVING count(l.id) <> cardinality(s.allowed_types)
    ) THEN
        RAISE EXCEPTION 'Group type backfill count mismatch';
    END IF;
END
$$;
DROP FUNCTION pg_temp.setting_type_uuid_v7(TIMESTAMPTZ);

CREATE OR REPLACE FUNCTION check_group_delivery_route() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM notification_settings s
        JOIN notifications n ON n.tenant_id = s.tenant_id AND n.id = NEW.notification_id
        JOIN notification_setting_types link ON link.setting_id = s.id
        JOIN notification_alert_rules r ON r.id = link.rule_id AND r.type = n.type AND r.tenant_id = n.tenant_id
        WHERE s.tenant_id = NEW.tenant_id AND s.id = NEW.setting_id AND s.channel = NEW.channel
          AND s.enabled AND s.deleted_at IS NULL AND n.deleted_at IS NULL
          AND r.enabled AND r.deleted_at IS NULL AND NEW.channel = ANY(r.channels)
    ) THEN
        RAISE EXCEPTION '% group is not configured for this notification type', NEW.channel
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END
$$;

ALTER TABLE notification_settings DROP COLUMN allowed_types;
