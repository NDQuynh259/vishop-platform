-- IDs are supplied by the application. IAM identities are external references;
-- only local relationships have foreign keys, always including tenant_id.
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    source_event_id VARCHAR(100) NOT NULL,
    type VARCHAR(100) NOT NULL CHECK (btrim(type) <> ''),
    subject_type VARCHAR(100) NOT NULL,
    subject_id VARCHAR(200) NOT NULL,
    shop_id UUID,
    required_permission VARCHAR(150) NOT NULL CHECK (btrim(required_permission) <> ''),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    title VARCHAR(300) NOT NULL CHECK (btrim(title) <> ''),
    body TEXT NOT NULL,
    action_url VARCHAR(2000),
    metadata JSONB NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(metadata) = 'object'),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, source_event_id, type, subject_type, subject_id)
);
CREATE INDEX idx_notifications_type ON notifications (tenant_id, type, created_at DESC, id DESC);

-- One row per in-app recipient; read state never lives on the shared notification.
CREATE TABLE notification_recipients (
    tenant_id UUID NOT NULL,
    notification_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, notification_id, user_id),
    FOREIGN KEY (tenant_id, notification_id) REFERENCES notifications (tenant_id, id),
    CHECK (read_at IS NULL OR read_at >= created_at)
);
CREATE INDEX idx_notification_recipient_feed
    ON notification_recipients (tenant_id, user_id, created_at DESC, notification_id DESC);
CREATE INDEX idx_notification_recipient_unread
    ON notification_recipients (tenant_id, user_id, created_at DESC, notification_id DESC)
    WHERE read_at IS NULL;

CREATE TABLE notification_alert_rules (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    type VARCHAR(100) NOT NULL CHECK (btrim(type) <> ''),
    enabled BOOLEAN NOT NULL DEFAULT true,
    in_app_enabled BOOLEAN NOT NULL DEFAULT true,
    email_enabled BOOLEAN NOT NULL DEFAULT false,
    thresholds JSONB NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(thresholds) = 'object'),
    cooldown_seconds INTEGER NOT NULL DEFAULT 86400 CHECK (cooldown_seconds >= 86400),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, type)
);

CREATE TABLE notification_alert_rule_recipients (
    tenant_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, rule_id, user_id),
    FOREIGN KEY (tenant_id, rule_id) REFERENCES notification_alert_rules (tenant_id, id)
);

-- Lock/upsert this key in the consumer transaction to enforce the rolling cooldown.
-- Scope per recipient so newly eligible users are not suppressed by another user.
CREATE TABLE notification_deduplication (
    tenant_id UUID NOT NULL,
    type VARCHAR(100) NOT NULL,
    subject_type VARCHAR(100) NOT NULL,
    subject_id VARCHAR(200) NOT NULL,
    user_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL')),
    last_notified_at TIMESTAMPTZ NOT NULL,
    last_severity VARCHAR(20) NOT NULL CHECK (last_severity IN ('INFO', 'WARNING', 'CRITICAL')),
    PRIMARY KEY (tenant_id, type, subject_type, subject_id, user_id, channel)
);

-- Durable email queue; null user_id permits transactional emails to invitees.
-- In-app content is persisted above, not sent through the provider queue.
CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    notification_id UUID NOT NULL,
    user_id UUID,
    channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL' CHECK (channel = 'EMAIL'),
    destination VARCHAR(320) NOT NULL CHECK (btrim(destination) <> ''),
    template_code VARCHAR(100) NOT NULL CHECK (btrim(template_code) <> ''),
    template_version INTEGER NOT NULL DEFAULT 1 CHECK (template_version > 0),
    template_data JSONB NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(template_data) = 'object'),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELLED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts > 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    lock_token UUID,
    provider_message_id VARCHAR(300),
    last_error TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, notification_id) REFERENCES notifications (tenant_id, id),
    CHECK (attempt_count <= max_attempts),
    CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CHECK ((status = 'PROCESSING') = (locked_until IS NOT NULL AND lock_token IS NOT NULL)),
    CHECK ((locked_until IS NULL) = (lock_token IS NULL))
);
CREATE UNIQUE INDEX uq_notification_delivery_destination
    ON notification_deliveries (tenant_id, notification_id, channel, lower(btrim(destination)));
CREATE INDEX idx_notification_deliveries_pending
    ON notification_deliveries (next_attempt_at, id) WHERE status = 'PENDING';
CREATE INDEX idx_notification_deliveries_expired_lease
    ON notification_deliveries (locked_until, id) WHERE status = 'PROCESSING';
CREATE INDEX idx_notification_deliveries_history
    ON notification_deliveries (tenant_id, notification_id, created_at DESC, id DESC);

CREATE TABLE notification_delivery_attempts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SENT', 'FAILED', 'UNKNOWN')),
    error_message TEXT,
    provider_message_id VARCHAR(300),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL CHECK (finished_at >= started_at),
    UNIQUE (tenant_id, delivery_id, attempt_number),
    FOREIGN KEY (tenant_id, delivery_id) REFERENCES notification_deliveries (tenant_id, id)
);

-- Compatible with common-persistence's idempotent consumer and outbox entities.
CREATE TABLE consumed_messages (
    message_id VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, consumer_group)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    event_type VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    error_message VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_notification_outbox_pending
    ON outbox_events (occurred_at, id) WHERE status = 'PENDING';
