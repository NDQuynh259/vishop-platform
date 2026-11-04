-- Catalog definitions now live in NotificationFeatureCode/NotificationTypeCode.
-- Preserve all stored business codes, tenant settings, recipients and delivery history.
ALTER TABLE notification_tenant_features DROP CONSTRAINT notification_tenant_features_feature_code_fkey;
ALTER TABLE notifications DROP CONSTRAINT fk_notifications_type;
ALTER TABLE notification_alert_rules DROP CONSTRAINT fk_notification_alert_rule_type;
ALTER TABLE notification_user_preferences DROP CONSTRAINT fk_notification_setting_type;
ALTER TABLE notification_deduplication DROP CONSTRAINT fk_notification_deduplication_type;

ALTER TABLE notification_tenant_features ADD CONSTRAINT notification_tenant_feature_code_not_blank
    CHECK (btrim(feature_code) <> '');
ALTER TABLE notification_deduplication ADD CONSTRAINT notification_deduplication_type_not_blank
    CHECK (btrim(type) <> '');

-- Type -> feature is an internal FK and disappears with the two catalog tables.
DROP TABLE notification_types;
DROP TABLE notification_features;

-- No SQL enum/check listing supported codes: code releases define support.
-- Inbound application boundaries validate new codes; legacy codes remain readable
-- in stored settings/history and must not become eligible for dispatch implicitly.
