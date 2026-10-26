-- Shared BaseJpaEntity metadata. Existing rows remain undeleted (NULL).
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE tenants ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE tenant_members ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE roles ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE tenant_invitations ADD COLUMN deleted_at TIMESTAMPTZ;
