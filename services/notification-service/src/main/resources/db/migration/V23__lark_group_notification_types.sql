ALTER TABLE notification_settings
    ADD COLUMN allowed_types TEXT[] NOT NULL DEFAULT '{}',
    ADD CONSTRAINT ck_notification_settings_lark_types
        CHECK (channel = 'LARK' OR cardinality(allowed_types) = 0);

-- Recheck the current group and tenant rule even for callers that enqueue directly.
CREATE FUNCTION check_lark_delivery_route() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM notification_settings s
        JOIN notifications n ON n.tenant_id = s.tenant_id AND n.id = NEW.notification_id
        JOIN notification_alert_rules r ON r.tenant_id = n.tenant_id AND r.type = n.type
        WHERE s.tenant_id = NEW.tenant_id AND s.id = NEW.setting_id AND s.channel = 'LARK'
          AND s.enabled AND s.deleted_at IS NULL AND n.deleted_at IS NULL
          AND n.type = ANY(s.allowed_types)
          AND r.enabled AND r.deleted_at IS NULL AND 'LARK' = ANY(r.channels)
    ) THEN
        RAISE EXCEPTION 'Lark group is not configured for this notification type'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER validate_lark_delivery_insert AFTER INSERT ON notification_deliveries
    FOR EACH ROW WHEN (NEW.channel = 'LARK') EXECUTE FUNCTION check_lark_delivery_route();

CREATE TRIGGER validate_lark_delivery_claim BEFORE UPDATE OF status ON notification_deliveries
    FOR EACH ROW WHEN (NEW.channel = 'LARK' AND NEW.status = 'PROCESSING')
    EXECUTE FUNCTION check_lark_delivery_route();
