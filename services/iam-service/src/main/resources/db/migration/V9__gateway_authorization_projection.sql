-- Durable, coalescing authorization outbox. No session table and no per-user role fan-out.
ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 1 CHECK (token_version > 0);
CREATE TABLE authorization_outbox (
    state_key VARCHAR(180) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    payload JSONB NOT NULL DEFAULT '{}',
    pending BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_authorization_outbox_pending ON authorization_outbox(updated_at) WHERE pending;

CREATE FUNCTION iam_authorization_refresh(kind TEXT, object_id UUID, tenant UUID DEFAULT NULL,
    member_user UUID DEFAULT NULL) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE key TEXT; state JSONB; revision BIGINT;
BEGIN
    key := CASE WHEN kind='member' THEN 'auth:member:' || tenant || ':' || member_user
                WHEN kind='role' THEN 'auth:role:' || tenant || ':' || object_id
                ELSE 'auth:' || kind || ':' || object_id END;
    -- Serialize writers of the same projection BEFORE reading its facts.
    INSERT INTO authorization_outbox(state_key) VALUES(key) ON CONFLICT DO NOTHING;
    UPDATE authorization_outbox SET version=version+1,pending=true,updated_at=clock_timestamp()
      WHERE state_key=key RETURNING version INTO revision;
    CASE kind
      WHEN 'user' THEN
        SELECT jsonb_build_object('status',status,'systemRole',system_role,'tokenVersion',token_version)
          INTO state FROM users WHERE id=object_id;
      WHEN 'tenant' THEN
        SELECT jsonb_build_object('status',status) INTO state FROM tenants WHERE id=object_id;
      WHEN 'member' THEN
        SELECT jsonb_build_object('status',status,'roleId',role_id) INTO state
          FROM tenant_members WHERE tenant_id=tenant AND user_id=member_user;
      WHEN 'role' THEN
        SELECT jsonb_build_object('status','ACTIVE','roleId',r.id,'roleKey',coalesce(r.builtin_key,'CUSTOM:' || r.id),
          'permissions',coalesce((SELECT jsonb_agg(permission_key ORDER BY permission_key)
             FROM role_permissions WHERE role_id=r.id),'[]'::jsonb)) INTO state
          FROM roles r WHERE r.id=object_id AND r.tenant_id=tenant;
      ELSE RAISE EXCEPTION 'Unknown authorization projection';
    END CASE;
    UPDATE authorization_outbox SET payload=coalesce(state,jsonb_build_object('status','INACTIVE'))
      || jsonb_build_object('key',key,'version',revision::TEXT)
      WHERE state_key=key;
END $$;

CREATE FUNCTION iam_user_token_version() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.password_hash IS DISTINCT FROM OLD.password_hash OR NEW.status IS DISTINCT FROM OLD.status
       OR NEW.system_role IS DISTINCT FROM OLD.system_role THEN
      NEW.token_version := OLD.token_version+1;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER auth_user_version BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION iam_user_token_version();

CREATE FUNCTION iam_authorization_changed() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE row_data JSONB; role_tenant UUID;
BEGIN
    row_data := CASE WHEN TG_OP='DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
    CASE TG_TABLE_NAME
      WHEN 'users' THEN
        IF TG_OP='UPDATE' AND NEW.token_version=OLD.token_version THEN RETURN NULL; END IF;
        PERFORM iam_authorization_refresh('user',(row_data->>'id')::UUID);
      WHEN 'tenants' THEN
        IF TG_OP='UPDATE' AND NEW.status=OLD.status THEN RETURN NULL; END IF;
        PERFORM iam_authorization_refresh('tenant',(row_data->>'id')::UUID);
      WHEN 'tenant_members' THEN
        PERFORM iam_authorization_refresh('member',NULL,(row_data->>'tenant_id')::UUID,(row_data->>'user_id')::UUID);
        IF TG_OP='UPDATE' AND (NEW.tenant_id,NEW.user_id) IS DISTINCT FROM (OLD.tenant_id,OLD.user_id) THEN
          PERFORM iam_authorization_refresh('member',NULL,OLD.tenant_id,OLD.user_id);
        END IF;
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
CREATE TRIGGER auth_user AFTER INSERT OR UPDATE OR DELETE ON users FOR EACH ROW EXECUTE FUNCTION iam_authorization_changed();
CREATE TRIGGER auth_tenant AFTER INSERT OR UPDATE OR DELETE ON tenants FOR EACH ROW EXECUTE FUNCTION iam_authorization_changed();
CREATE TRIGGER auth_member AFTER INSERT OR UPDATE OR DELETE ON tenant_members FOR EACH ROW EXECUTE FUNCTION iam_authorization_changed();
CREATE TRIGGER auth_role AFTER INSERT OR UPDATE OR DELETE ON roles FOR EACH ROW EXECUTE FUNCTION iam_authorization_changed();
CREATE TRIGGER auth_permission AFTER INSERT OR UPDATE OR DELETE ON role_permissions FOR EACH ROW EXECUTE FUNCTION iam_authorization_changed();

SELECT iam_authorization_refresh('user',id) FROM users ORDER BY id;
SELECT iam_authorization_refresh('tenant',id) FROM tenants ORDER BY id;
SELECT iam_authorization_refresh('role',id,tenant_id) FROM roles ORDER BY id;
SELECT iam_authorization_refresh('member',NULL,tenant_id,user_id) FROM tenant_members ORDER BY id;
