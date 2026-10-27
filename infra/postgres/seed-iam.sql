-- =============================================================================
-- ViShop Full IAM Database Seed Script (Local Development & Testing)
-- Database: vishop_iam
-- All passwords: Password123! (except Admin: AdminPassword123!)
-- Covers: users, tenants, roles (builtin & custom), role_permissions,
--         tenant_members, and tenant_invitations.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 1. USERS
-- -----------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name, system_role, status)
VALUES
    (
        'a0000000-0000-0000-0000-000000000001',
        'admin@vishop.com',
        crypt('AdminPassword123!', gen_salt('bf', 10)),
        'Platform Administrator',
        'PLATFORM_ADMIN',
        'ACTIVE'
    ),
    (
        'a0000000-0000-0000-0000-000000000002',
        'owner@example.com',
        crypt('Password123!', gen_salt('bf', 10)),
        'Shop Owner',
        'USER',
        'ACTIVE'
    ),
    (
        'a0000000-0000-0000-0000-000000000003',
        'staff@example.com',
        crypt('Password123!', gen_salt('bf', 10)),
        'Operations Staff',
        'USER',
        'ACTIVE'
    ),
    (
        'a0000000-0000-0000-0000-000000000004',
        'accountant@example.com',
        crypt('Password123!', gen_salt('bf', 10)),
        'Finance Accountant',
        'USER',
        'ACTIVE'
    ),
    (
        'a0000000-0000-0000-0000-000000000005',
        'support@example.com',
        crypt('Password123!', gen_salt('bf', 10)),
        'Customer Support',
        'USER',
        'ACTIVE'
    ),
    (
        'a0000000-0000-0000-0000-000000000006',
        'user@example.com',
        crypt('StrongPass123!', gen_salt('bf', 10)),
        'Warehouse Keeper',
        'USER',
        'ACTIVE'
    )
ON CONFLICT (lower(email)) DO UPDATE
SET
    password_hash = EXCLUDED.password_hash,
    full_name     = EXCLUDED.full_name,
    system_role   = EXCLUDED.system_role,
    status        = EXCLUDED.status,
    updated_at    = now();

-- -----------------------------------------------------------------------------
-- 2. TENANTS
-- -----------------------------------------------------------------------------
-- Note: Trigger trg_tenant_builtin_roles creates OWNER, OPERATIONS, WAREHOUSE,
-- ACCOUNTING roles automatically upon tenant insert.
INSERT INTO tenants (id, code, name, status)
VALUES
    (
        'b0000000-0000-0000-0000-000000000001',
        'vishop-official',
        'ViShop Official Store',
        'ACTIVE'
    ),
    (
        'b0000000-0000-0000-0000-000000000002',
        'demo-store',
        'Demo Fashion Store',
        'ACTIVE'
    )
ON CONFLICT (code) DO UPDATE
SET
    name       = EXCLUDED.name,
    status     = EXCLUDED.status,
    updated_at = now();

-- -----------------------------------------------------------------------------
-- 3. CUSTOM ROLES & PERMISSIONS
-- -----------------------------------------------------------------------------
-- Insert custom "Customer Care" role in vishop-official
INSERT INTO roles (id, tenant_id, name, builtin_key, require_two_factor)
SELECT
    'd0000000-0000-0000-0000-000000000001'::uuid,
    t.id,
    'Customer Care',
    NULL,
    false
FROM tenants t WHERE t.code = 'vishop-official'
ON CONFLICT (tenant_id, lower(name)) DO NOTHING;

-- Assign permissions to custom "Customer Care" role
INSERT INTO role_permissions (role_id, permission_key)
SELECT r.id, p.perm
FROM roles r
CROSS JOIN (
    VALUES
        ('order.view'),
        ('product.view'),
        ('tenant.view'),
        ('shop.view'),
        ('audit_log.view')
) AS p(perm)
WHERE r.id = 'd0000000-0000-0000-0000-000000000001'::uuid
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. TENANT MEMBERSHIPS
-- -----------------------------------------------------------------------------

-- owner@example.com -> OWNER of vishop-official
INSERT INTO tenant_members (id, tenant_id, user_id, role_id, status)
SELECT
    'c0000000-0000-0000-0000-000000000001'::uuid,
    t.id, u.id, r.id, 'ACTIVE'
FROM tenants t
JOIN users u ON u.email = 'owner@example.com'
JOIN roles r ON r.tenant_id = t.id AND r.builtin_key = 'OWNER'
WHERE t.code = 'vishop-official'
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET role_id = EXCLUDED.role_id, status = 'ACTIVE';

-- owner@example.com -> OWNER of demo-store
INSERT INTO tenant_members (id, tenant_id, user_id, role_id, status)
SELECT
    'c0000000-0000-0000-0000-000000000002'::uuid,
    t.id, u.id, r.id, 'ACTIVE'
FROM tenants t
JOIN users u ON u.email = 'owner@example.com'
JOIN roles r ON r.tenant_id = t.id AND r.builtin_key = 'OWNER'
WHERE t.code = 'demo-store'
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET role_id = EXCLUDED.role_id, status = 'ACTIVE';

-- staff@example.com -> OPERATIONS of vishop-official
INSERT INTO tenant_members (id, tenant_id, user_id, role_id, status)
SELECT
    'c0000000-0000-0000-0000-000000000003'::uuid,
    t.id, u.id, r.id, 'ACTIVE'
FROM tenants t
JOIN users u ON u.email = 'staff@example.com'
JOIN roles r ON r.tenant_id = t.id AND r.builtin_key = 'OPERATIONS'
WHERE t.code = 'vishop-official'
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET role_id = EXCLUDED.role_id, status = 'ACTIVE';

-- user@example.com -> WAREHOUSE of vishop-official
INSERT INTO tenant_members (id, tenant_id, user_id, role_id, status)
SELECT
    'c0000000-0000-0000-0000-000000000004'::uuid,
    t.id, u.id, r.id, 'ACTIVE'
FROM tenants t
JOIN users u ON u.email = 'user@example.com'
JOIN roles r ON r.tenant_id = t.id AND r.builtin_key = 'WAREHOUSE'
WHERE t.code = 'vishop-official'
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET role_id = EXCLUDED.role_id, status = 'ACTIVE';

-- accountant@example.com -> ACCOUNTING of vishop-official
INSERT INTO tenant_members (id, tenant_id, user_id, role_id, status)
SELECT
    'c0000000-0000-0000-0000-000000000005'::uuid,
    t.id, u.id, r.id, 'ACTIVE'
FROM tenants t
JOIN users u ON u.email = 'accountant@example.com'
JOIN roles r ON r.tenant_id = t.id AND r.builtin_key = 'ACCOUNTING'
WHERE t.code = 'vishop-official'
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET role_id = EXCLUDED.role_id, status = 'ACTIVE';

-- support@example.com -> Custom "Customer Care" of vishop-official
INSERT INTO tenant_members (id, tenant_id, user_id, role_id, status)
SELECT
    'c0000000-0000-0000-0000-000000000006'::uuid,
    t.id, u.id, r.id, 'ACTIVE'
FROM tenants t
JOIN users u ON u.email = 'support@example.com'
JOIN roles r ON r.tenant_id = t.id AND r.name = 'Customer Care'
WHERE t.code = 'vishop-official'
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET role_id = EXCLUDED.role_id, status = 'ACTIVE';

-- Set default tenant preference for active members
UPDATE users u
SET last_tenant_id = (SELECT id FROM tenants WHERE code = 'vishop-official')
WHERE u.email IN ('owner@example.com', 'staff@example.com', 'accountant@example.com', 'support@example.com', 'user@example.com');

-- -----------------------------------------------------------------------------
-- 5. TENANT INVITATIONS (Pending, Accepted, Expired)
-- -----------------------------------------------------------------------------

-- 5.1 Pending Invitation for new_member@example.com
INSERT INTO tenant_invitations (
    id, tenant_id, email, role_id, invited_by,
    token_hash, status, expires_at, created_at
)
SELECT
    'e0000000-0000-0000-0000-000000000001'::uuid,
    t.id,
    'new_member@example.com',
    r.id,
    u.id,
    -- 64-char sha256 hex string for invitation token
    encode(digest('invitation-token-pending-1234567890', 'sha256'), 'hex'),
    'PENDING',
    now() + interval '7 days',
    now()
FROM tenants t
JOIN users u ON u.email = 'owner@example.com'
JOIN roles r ON r.tenant_id = t.id AND r.builtin_key = 'OPERATIONS'
WHERE t.code = 'vishop-official'
ON CONFLICT (token_hash) DO NOTHING;

-- 5.2 Expired Invitation for old_invitee@example.com
INSERT INTO tenant_invitations (
    id, tenant_id, email, role_id, invited_by,
    token_hash, status, expires_at, created_at
)
SELECT
    'e0000000-0000-0000-0000-000000000002'::uuid,
    t.id,
    'old_invitee@example.com',
    r.id,
    u.id,
    encode(digest('invitation-token-expired-1234567890', 'sha256'), 'hex'),
    'EXPIRED',
    now() - interval '1 day',
    now() - interval '8 days'
FROM tenants t
JOIN users u ON u.email = 'owner@example.com'
JOIN roles r ON r.tenant_id = t.id AND r.builtin_key = 'WAREHOUSE'
WHERE t.code = 'vishop-official'
ON CONFLICT (token_hash) DO NOTHING;

-- 5.3 Accepted Invitation for support@example.com
INSERT INTO tenant_invitations (
    id, tenant_id, email, role_id, invited_by,
    token_hash, status, expires_at, accepted_by, accepted_at, created_at
)
SELECT
    'e0000000-0000-0000-0000-000000000003'::uuid,
    t.id,
    'support@example.com',
    r.id,
    inviter.id,
    encode(digest('invitation-token-accepted-1234567890', 'sha256'), 'hex'),
    'ACCEPTED',
    now() + interval '5 days',
    acceptor.id,
    now() - interval '1 hour',
    now() - interval '2 days'
FROM tenants t
JOIN users inviter ON inviter.email = 'owner@example.com'
JOIN users acceptor ON acceptor.email = 'support@example.com'
JOIN roles r ON r.tenant_id = t.id AND r.name = 'Customer Care'
WHERE t.code = 'vishop-official'
ON CONFLICT (token_hash) DO NOTHING;
