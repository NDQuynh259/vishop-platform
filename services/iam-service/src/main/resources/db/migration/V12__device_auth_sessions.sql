-- One login session per device; JWT remains the credential format.
-- This migration adds revocation state, not Spring HttpSession or sticky routing.
CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_version BIGINT NOT NULL CHECK (token_version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (expires_at > created_at)
);
CREATE INDEX idx_auth_sessions_user ON auth_sessions(user_id);
CREATE INDEX idx_auth_sessions_expiry ON auth_sessions(expires_at);

-- Reuse the durable per-key revision protocol; never publish uncommitted session state.
CREATE FUNCTION iam_session_changed() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE facts JSONB; key TEXT; revision BIGINT;
BEGIN
    facts := CASE WHEN TG_OP='DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
    key := 'auth:session:' || (facts->>'id');
    INSERT INTO authorization_outbox(state_key) VALUES(key) ON CONFLICT DO NOTHING;
    UPDATE authorization_outbox SET version=version+1,pending=true,updated_at=clock_timestamp()
      WHERE state_key=key RETURNING version INTO revision;
    UPDATE authorization_outbox SET payload=jsonb_build_object(
      'key',key,'version',revision::TEXT,
      'status',CASE WHEN TG_OP='DELETE' OR facts->>'revoked_at' IS NOT NULL THEN 'REVOKED'
                    WHEN (facts->>'expires_at')::TIMESTAMPTZ <= clock_timestamp() THEN 'EXPIRED' ELSE 'ACTIVE' END,
      'userId',facts->>'user_id','tokenVersion',(facts->>'token_version')::BIGINT,
      'expiresAt',facts->'expires_at') WHERE state_key=key;
    RETURN NULL;
END $$;
CREATE TRIGGER auth_session AFTER INSERT OR UPDATE OR DELETE ON auth_sessions
    FOR EACH ROW EXECUTE FUNCTION iam_session_changed();

-- A soft-deleted account must revoke credentials just like a locked account.
CREATE OR REPLACE FUNCTION iam_user_token_version() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.password_hash IS DISTINCT FROM OLD.password_hash OR NEW.status IS DISTINCT FROM OLD.status
       OR NEW.system_role IS DISTINCT FROM OLD.system_role OR NEW.deleted_at IS DISTINCT FROM OLD.deleted_at THEN
      NEW.token_version := OLD.token_version+1;
    END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION iam_authorization_refresh(kind TEXT, object_id UUID, tenant UUID DEFAULT NULL,
    member_user UUID DEFAULT NULL) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE key TEXT; state JSONB; revision BIGINT;
BEGIN
    key := CASE WHEN kind='role' THEN 'auth:role:' || tenant || ':' || object_id
                ELSE 'auth:' || kind || ':' || object_id END;
    -- Serialize writers of the same projection BEFORE reading its facts.
    INSERT INTO authorization_outbox(state_key) VALUES(key) ON CONFLICT DO NOTHING;
    UPDATE authorization_outbox SET version=version+1,pending=true,updated_at=clock_timestamp()
      WHERE state_key=key RETURNING version INTO revision;
    CASE kind
      WHEN 'user' THEN
        SELECT jsonb_build_object('status',CASE WHEN deleted_at IS NULL THEN status ELSE 'INACTIVE' END,'systemRole',system_role,'tokenVersion',token_version)
          INTO state FROM users WHERE id=object_id;
      WHEN 'tenant' THEN
        SELECT jsonb_build_object('status',status) INTO state FROM tenants WHERE id=object_id AND deleted_at IS NULL;
      WHEN 'role' THEN
        SELECT jsonb_build_object('status','ACTIVE','roleId',r.id,'roleKey',coalesce(r.builtin_key,'CUSTOM:' || r.id),
          'permissions',coalesce((SELECT jsonb_agg(permission_key ORDER BY permission_key)
             FROM role_permissions WHERE role_id=r.id),'[]'::jsonb)) INTO state
          FROM roles r WHERE r.id=object_id AND r.tenant_id=tenant AND r.deleted_at IS NULL AND NOT r.require_two_factor;
      ELSE RAISE EXCEPTION 'Unknown authorization projection';
    END CASE;
    UPDATE authorization_outbox SET payload=coalesce(state,jsonb_build_object('status','INACTIVE'))
      || jsonb_build_object('key',key,'version',revision::TEXT)
      WHERE state_key=key;
END $$;

CREATE OR REPLACE FUNCTION iam_authorization_changed() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE row_data JSONB; role_tenant UUID;
BEGIN
    row_data := CASE WHEN TG_OP='DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
    CASE TG_TABLE_NAME
      WHEN 'users' THEN
        IF TG_OP='UPDATE' AND NEW.token_version=OLD.token_version THEN RETURN NULL; END IF;
        PERFORM iam_authorization_refresh('user',(row_data->>'id')::UUID);
      WHEN 'tenants' THEN
        IF TG_OP='UPDATE' AND NEW.status=OLD.status AND NEW.deleted_at IS NOT DISTINCT FROM OLD.deleted_at THEN RETURN NULL; END IF;
        PERFORM iam_authorization_refresh('tenant',(row_data->>'id')::UUID);
      WHEN 'roles' THEN
        PERFORM iam_authorization_refresh('role',(row_data->>'id')::UUID,(row_data->>'tenant_id')::UUID);
      WHEN 'role_permissions' THEN
        SELECT tenant_id INTO role_tenant FROM roles WHERE id=(row_data->>'role_id')::UUID;
        IF role_tenant IS NOT NULL THEN
          PERFORM iam_authorization_refresh('role',(row_data->>'role_id')::UUID,role_tenant);
        END IF;
        IF TG_OP='UPDATE' AND NEW.role_id IS DISTINCT FROM OLD.role_id THEN
          SELECT tenant_id INTO role_tenant FROM roles WHERE id=OLD.role_id;
          IF role_tenant IS NOT NULL THEN PERFORM iam_authorization_refresh('role',OLD.role_id,role_tenant); END IF;
        END IF;
    END CASE;
    RETURN NULL;
END $$;

-- Rebuild existing snapshots using the stricter soft-delete/MFA status rules above.
SELECT iam_authorization_refresh('user',id) FROM users ORDER BY id;
SELECT iam_authorization_refresh('tenant',id) FROM tenants ORDER BY id;
SELECT iam_authorization_refresh('role',id,tenant_id) FROM roles ORDER BY id;
