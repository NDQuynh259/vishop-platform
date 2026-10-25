-- Stateless JWT authentication replaces session storage and Redis security projections.
DROP TRIGGER IF EXISTS security_user ON users;
DROP TRIGGER IF EXISTS security_tenant ON tenants;
DROP TRIGGER IF EXISTS security_member ON tenant_members;
DROP TRIGGER IF EXISTS security_role ON roles;
DROP TRIGGER IF EXISTS security_permission ON role_permissions;
DROP TABLE sessions;
DROP FUNCTION iam_security_session();
DROP FUNCTION iam_security_user();
DROP FUNCTION iam_security_tenant();
DROP FUNCTION iam_security_member();
DROP FUNCTION iam_security_role();
DROP FUNCTION iam_security_change(TEXT,UUID,TEXT,TIMESTAMPTZ);
DROP TABLE security_states;
