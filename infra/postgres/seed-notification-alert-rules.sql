-- Run with psql -v tenant_id=<tenant UUID> against vishop_notification after V26.
\set ON_ERROR_STOP on
BEGIN;

CREATE FUNCTION pg_temp.seed_rule_uuid_v7(created_at TIMESTAMPTZ)
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

INSERT INTO notification_alert_rules (id, tenant_id, type, feature_code)
SELECT pg_temp.seed_rule_uuid_v7(now()), :'tenant_id'::uuid, rule.type, rule.feature_code
FROM (VALUES
    ('SYSTEM_ANNOUNCEMENT', 'SYSTEM'),
    ('SYSTEM_MAINTENANCE', 'SYSTEM'),
    ('SYSTEM_INCIDENT', 'SYSTEM'),
    ('MEMBER_INVITED', 'IAM'),
    ('SUBSCRIPTION_ACTIVATED', 'SUBSCRIPTION'),
    ('SUBSCRIPTION_EXPIRING', 'SUBSCRIPTION'),
    ('AUTHORIZATION_EXPIRING', 'SHOP'),
    ('STOCK_PUSH_FAILED', 'INVENTORY'),
    ('STOCK_LOW', 'INVENTORY'),
    ('PACKAGE_STOCK_SHORTAGE', 'FULFILLMENT'),
    ('SHIPMENT_EXCEPTION', 'FULFILLMENT'),
    ('SKU_UNMAPPED', 'PRODUCT'),
    ('ORDER_LOSS', 'FINANCE'),
    ('RECONCILIATION_MISMATCH', 'FINANCE'),
    ('SYNC_FAILED', 'SYNC')
) AS rule(type, feature_code)
ON CONFLICT (tenant_id, type) DO NOTHING;

DROP FUNCTION pg_temp.seed_rule_uuid_v7(timestamptz);
COMMIT;
