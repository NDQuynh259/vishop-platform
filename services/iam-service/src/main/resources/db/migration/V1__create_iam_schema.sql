-- IAM owns global identities and tenant access in the same PostgreSQL database.
-- This is a NEW Flyway history; never apply legacy identity/tenant V1 migrations here.

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL CHECK (email = btrim(email) AND email <> ''),
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL CHECK (btrim(full_name) <> ''),
    system_role VARCHAR(30) NOT NULL DEFAULT 'USER'
        CHECK (system_role IN ('USER', 'PLATFORM_ADMIN')),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE
        CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$'),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE INDEX idx_tenants_cursor ON tenants (created_at DESC, id DESC);
CREATE INDEX idx_tenants_status ON tenants (status);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    builtin_key VARCHAR(30)
        CHECK (builtin_key IN ('OWNER', 'OPERATIONS', 'WAREHOUSE', 'ACCOUNTING')),
    require_two_factor BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_roles_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_roles_tenant_name ON roles (tenant_id, lower(name));
CREATE UNIQUE INDEX uq_roles_builtin ON roles (tenant_id, builtin_key) WHERE builtin_key IS NOT NULL;

-- Permissions apply ONLY to tenant roles. No system-role permission tables exist.
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_key),
    CONSTRAINT ck_tenant_permission CHECK (permission_key IN (
        'order.view', 'order.create', 'order.update', 'order.cancel',
        'order.package.ship', 'order.print', 'order.split', 'order.export',
        'product.view', 'product.create', 'product.update', 'product.delete',
        'product.view_cost_price', 'product.edit_cost_price', 'product.link_channel',
        'inventory.view', 'inventory.adjust', 'inventory.transfer', 'inventory.stock_take',
        'tenant.view', 'tenant.manage', 'tenant.member.view', 'tenant.member.invite',
        'tenant.member.role_assign', 'tenant.member.remove',
        'shop.view', 'shop.connect', 'shop.disconnect', 'shop.settings',
        'finance.settlement.view', 'finance.settlement.export', 'finance.reconciliation',
        'audit_log.view', 'audit_log.export'
    ))
);

CREATE TABLE tenant_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_tenant_members_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_member_role_tenant FOREIGN KEY (tenant_id, role_id)
        REFERENCES roles (tenant_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_members_user_status ON tenant_members (user_id, status, tenant_id);
CREATE INDEX idx_members_role ON tenant_members (tenant_id, role_id);

CREATE TABLE tenant_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    email VARCHAR(255) NOT NULL CHECK (email = btrim(email) AND email <> ''),
    role_id UUID NOT NULL,
    invited_by UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT fk_invitation_role_tenant FOREIGN KEY (tenant_id, role_id)
        REFERENCES roles (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_invitation_inviter_tenant FOREIGN KEY (tenant_id, invited_by)
        REFERENCES tenant_members (tenant_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_invitation_acceptor_tenant FOREIGN KEY (tenant_id, accepted_by)
        REFERENCES tenant_members (tenant_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_invitation_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_invitation_acceptance CHECK (
        (status = 'ACCEPTED' AND accepted_by IS NOT NULL AND accepted_at IS NOT NULL
            AND accepted_at >= created_at AND accepted_at < expires_at)
        OR (status <> 'ACCEPTED' AND accepted_by IS NULL AND accepted_at IS NULL)
    )
);
CREATE UNIQUE INDEX uq_pending_invitation ON tenant_invitations (tenant_id, lower(email))
    WHERE status = 'PENDING';
CREATE INDEX idx_invitation_role ON tenant_invitations (tenant_id, role_id);
CREATE INDEX idx_invitation_inviter ON tenant_invitations (tenant_id, invited_by);
CREATE INDEX idx_invitation_acceptor ON tenant_invitations (tenant_id, accepted_by);
CREATE INDEX idx_invitation_expiry ON tenant_invitations (expires_at) WHERE status = 'PENDING';

-- Only a hash of the opaque session token is persisted. Tenant selection is per session.
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    active_tenant_id UUID,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_session_membership FOREIGN KEY (active_tenant_id, user_id)
        REFERENCES tenant_members (tenant_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_session_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_session_revocation CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
CREATE INDEX idx_sessions_user ON sessions (user_id);
CREATE INDEX idx_sessions_tenant_user ON sessions (active_tenant_id, user_id);
CREATE INDEX idx_sessions_expiry ON sessions (expires_at);

-- Same outbox/consumer contracts as common-persistence.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    event_type VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    error_message VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_events_pending ON outbox_events (occurred_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);

CREATE TABLE consumed_messages (
    message_id VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, consumer_group)
);
CREATE INDEX idx_consumed_messages_processed ON consumed_messages (processed_at);
