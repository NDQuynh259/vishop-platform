-- Personal delivery preferences; tenant alert rules still determine eligibility.
-- Missing preferences mean inherit enabled tenant channels. A preference can opt
-- out of a channel, never enable a channel disabled by the tenant rule.
CREATE TABLE notification_settings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    type VARCHAR(100) NOT NULL CHECK (btrim(type) <> ''),
    enabled BOOLEAN NOT NULL DEFAULT true,
    in_app_enabled BOOLEAN NOT NULL DEFAULT true,
    email_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (tenant_id, user_id, type)
);

COMMENT ON TABLE notification_settings IS
    'Per-tenant, per-user, per-type preferences. Application combines these with tenant rules and authorization.';
