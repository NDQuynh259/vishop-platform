-- Routing uses notification_alert_rules and notification_settings only.
-- These legacy settings have no runtime readers/writers. Their stored data is removed.
-- Keep inbox recipients, delivery history, cooldown and event-processing state.
DROP TABLE notification_alert_rule_recipients;
DROP TABLE notification_user_preferences;
DROP TABLE notification_tenant_features;

COMMENT ON TABLE notification_alert_rules IS
    'Tenant per-type notification switches and channel choices. Recipients are resolved by the dispatch boundary.';
