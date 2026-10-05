-- ============================================================================
-- V1__init_audit_schema.sql
-- Base schema for audit-service: audit_logs, consumed_messages
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ----------------------------------------------------------------------------
-- 1. Audit Logs Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    tenant_id UUID,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    producer VARCHAR(100),
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_event_id ON audit_logs (event_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant ON audit_logs (tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_aggregate ON audit_logs (aggregate_type, aggregate_id, occurred_at DESC);

-- ----------------------------------------------------------------------------
-- 2. Idempotent Consumed Messages Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS consumed_messages (
    message_id VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, consumer_group)
);

CREATE INDEX IF NOT EXISTS idx_audit_consumed_messages_processed ON consumed_messages (processed_at ASC);
