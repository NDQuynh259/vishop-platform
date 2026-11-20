ALTER TABLE notification_alert_rules ADD COLUMN feature_code VARCHAR(32);
UPDATE notification_alert_rules SET feature_code = CASE
    WHEN type IN ('SYSTEM_ANNOUNCEMENT', 'SYSTEM_MAINTENANCE', 'SYSTEM_INCIDENT') THEN 'SYSTEM'
    WHEN type IN ('MEMBER_INVITED') THEN 'IAM'
    WHEN type IN ('SUBSCRIPTION_ACTIVATED', 'SUBSCRIPTION_EXPIRING') THEN 'SUBSCRIPTION'
    WHEN type IN ('AUTHORIZATION_EXPIRING') THEN 'SHOP'
    WHEN type IN ('STOCK_PUSH_FAILED', 'STOCK_LOW') THEN 'INVENTORY'
    WHEN type IN ('PACKAGE_STOCK_SHORTAGE', 'SHIPMENT_EXCEPTION') THEN 'FULFILLMENT'
    WHEN type IN ('SKU_UNMAPPED') THEN 'PRODUCT'
    WHEN type IN ('ORDER_LOSS', 'RECONCILIATION_MISMATCH') THEN 'FINANCE'
    WHEN type IN ('SYNC_FAILED') THEN 'SYNC'
    ELSE 'LEGACY' END;
ALTER TABLE notification_alert_rules ALTER COLUMN feature_code SET NOT NULL,
    ADD CONSTRAINT ck_alert_rule_feature CHECK (feature_code IN ('SYSTEM', 'IAM', 'SUBSCRIPTION', 'SHOP', 'INVENTORY', 'FULFILLMENT', 'PRODUCT', 'FINANCE', 'SYNC', 'LEGACY'));

CREATE OR REPLACE FUNCTION check_group_delivery_route() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM notification_settings s
        JOIN notifications n ON n.tenant_id = s.tenant_id AND n.id = NEW.notification_id
        JOIN notification_setting_types link ON link.setting_id = s.id
        JOIN notification_alert_rules r ON r.id = link.rule_id AND r.type = n.type AND r.tenant_id = n.tenant_id
        WHERE s.tenant_id = NEW.tenant_id AND s.id = NEW.setting_id AND s.channel = NEW.channel
          AND s.enabled AND s.deleted_at IS NULL AND n.deleted_at IS NULL
          AND r.enabled AND r.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION '% group is not configured for this notification type', NEW.channel
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END
$$;

ALTER TABLE notification_alert_rules DROP COLUMN channels;
