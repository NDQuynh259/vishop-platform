CREATE TABLE notification_features (
    code VARCHAR(100) PRIMARY KEY CHECK (btrim(code) <> ''),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    description TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE notification_types (
    code VARCHAR(100) PRIMARY KEY CHECK (btrim(code) <> ''),
    feature_code VARCHAR(100) NOT NULL REFERENCES notification_features (code),
    category VARCHAR(20) NOT NULL CHECK (category IN ('SYSTEM', 'BUSINESS')),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    description TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT true,
    default_enabled BOOLEAN NOT NULL DEFAULT true,
    default_in_app_enabled BOOLEAN NOT NULL DEFAULT true,
    default_email_enabled BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_notification_types_feature ON notification_types (feature_code, display_order, code);

INSERT INTO notification_features (code, name, display_order) VALUES
    ('SYSTEM', 'Hệ thống', 10),
    ('IAM', 'Tài khoản và thành viên', 20),
    ('SUBSCRIPTION', 'Gói cước', 30),
    ('SHOP', 'Kết nối gian hàng', 40),
    ('INVENTORY', 'Kho hàng', 50),
    ('PRODUCT', 'Sản phẩm', 60),
    ('FULFILLMENT', 'Xử lý và vận chuyển đơn', 70),
    ('FINANCE', 'Tài chính và đối soát', 80),
    ('SYNC', 'Đồng bộ dữ liệu', 90),
    ('LEGACY', 'Loại thông báo hiện có khác', 1000);

INSERT INTO notification_types (code, feature_code, category, name, display_order) VALUES
    ('SYSTEM_ANNOUNCEMENT', 'SYSTEM', 'SYSTEM', 'Thông báo từ hệ thống', 10),
    ('SYSTEM_MAINTENANCE', 'SYSTEM', 'SYSTEM', 'Lịch bảo trì', 20),
    ('SYSTEM_INCIDENT', 'SYSTEM', 'SYSTEM', 'Sự cố hệ thống', 30),
    ('MEMBER_INVITED', 'IAM', 'BUSINESS', 'Lời mời thành viên', 10),
    ('SUBSCRIPTION_ACTIVATED', 'SUBSCRIPTION', 'BUSINESS', 'Kích hoạt gói cước', 10),
    ('SUBSCRIPTION_EXPIRING', 'SUBSCRIPTION', 'BUSINESS', 'Gói cước sắp hết hạn', 20),
    ('AUTHORIZATION_EXPIRING', 'SHOP', 'BUSINESS', 'Ủy quyền gian hàng sắp hết hạn', 10),
    ('STOCK_PUSH_FAILED', 'INVENTORY', 'BUSINESS', 'Đẩy tồn thất bại', 10),
    ('STOCK_LOW', 'INVENTORY', 'BUSINESS', 'Tồn kho dưới ngưỡng', 20),
    ('PACKAGE_STOCK_SHORTAGE', 'FULFILLMENT', 'BUSINESS', 'Kiện hàng thiếu tồn', 10),
    ('SHIPMENT_EXCEPTION', 'FULFILLMENT', 'BUSINESS', 'Vận chuyển bất thường', 20),
    ('SKU_UNMAPPED', 'PRODUCT', 'BUSINESS', 'SKU chưa ghép nối', 10),
    ('ORDER_LOSS', 'FINANCE', 'BUSINESS', 'Đơn hàng lỗ', 10),
    ('RECONCILIATION_MISMATCH', 'FINANCE', 'BUSINESS', 'Lệch đối soát', 20),
    ('SYNC_FAILED', 'SYNC', 'BUSINESS', 'Đồng bộ lỗi', 10);

-- Preserve previously configured/custom type codes before adding catalog FKs.
INSERT INTO notification_types (code, feature_code, category, name)
SELECT type, 'LEGACY', 'BUSINESS', type FROM (
    SELECT type FROM notifications
    UNION SELECT type FROM notification_alert_rules
    UNION SELECT type FROM notification_settings
    UNION SELECT type FROM notification_deduplication
) existing
ON CONFLICT (code) DO NOTHING;

ALTER TABLE notifications ADD CONSTRAINT fk_notifications_type
    FOREIGN KEY (type) REFERENCES notification_types (code);
ALTER TABLE notification_alert_rules ADD CONSTRAINT fk_notification_alert_rule_type
    FOREIGN KEY (type) REFERENCES notification_types (code);
ALTER TABLE notification_settings ADD CONSTRAINT fk_notification_setting_type
    FOREIGN KEY (type) REFERENCES notification_types (code);
ALTER TABLE notification_deduplication ADD CONSTRAINT fk_notification_deduplication_type
    FOREIGN KEY (type) REFERENCES notification_types (code);

-- Tenant-wide channel switches. Missing rows default to IN_APP on / EMAIL off.
-- Connection references point to external provider configuration, never credentials.
CREATE TABLE notification_tenant_channels (
    tenant_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL')),
    enabled BOOLEAN NOT NULL DEFAULT false,
    connection_ref VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, channel),
    CHECK (connection_ref IS NULL OR btrim(connection_ref) <> ''),
    CHECK (channel <> 'IN_APP' OR connection_ref IS NULL)
);

CREATE TABLE notification_tenant_features (
    tenant_id UUID NOT NULL,
    feature_code VARCHAR(100) NOT NULL REFERENCES notification_features (code),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, feature_code)
);

-- Preserve the effective email behavior of existing explicit per-type rules.
INSERT INTO notification_tenant_channels (tenant_id, channel, enabled)
SELECT tenant_id, 'EMAIL', bool_or(enabled AND email_enabled)
FROM notification_alert_rules GROUP BY tenant_id;

COMMENT ON TABLE notification_alert_rules IS
    'Tenant per-type settings and recipients; applies to both SYSTEM and BUSINESS catalog types.';
COMMENT ON TABLE notification_tenant_features IS
    'Tenant subscriptions to feature groups. Missing rows inherit enabled; disabled features suppress all their types.';
COMMENT ON TABLE notification_tenant_channels IS
    'Tenant-wide channel gate. Combine with feature, type, personal preferences and authorization.';
