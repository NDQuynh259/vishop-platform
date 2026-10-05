-- ============================================================================
-- V1__init_tenant_schema.sql
-- Base schema for tenant-service: tenants, outbox_events, consumed_messages
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ----------------------------------------------------------------------------
-- 1. Tenants Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- Index for cursor pagination (createdAt DESC, id DESC)
CREATE INDEX IF NOT EXISTS idx_tenants_cursor ON tenants (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants (status);

-- ----------------------------------------------------------------------------
-- 2. Transactional Outbox Events Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    event_type VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_pending ON outbox_events (occurred_at ASC)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);

-- ----------------------------------------------------------------------------
-- 3. Idempotent Consumed Messages Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS consumed_messages (
    message_id VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, consumer_group)
);

CREATE INDEX IF NOT EXISTS idx_consumed_messages_processed ON consumed_messages (processed_at ASC);
