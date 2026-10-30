-- Gateway now uses JWT roleId only after validating the global user tokenVersion.
-- Keep V10's membership revocation trigger: every access-changing UPDATE/DELETE revokes
-- access AND refresh credentials. Only the redundant member projection is retired.
DROP TRIGGER auth_member ON tenant_members;

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
        SELECT jsonb_build_object('status',status,'systemRole',system_role,'tokenVersion',token_version)
          INTO state FROM users WHERE id=object_id;
      WHEN 'tenant' THEN
        SELECT jsonb_build_object('status',status) INTO state FROM tenants WHERE id=object_id;
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

CREATE OR REPLACE FUNCTION iam_authorization_changed() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
-- These are derived snapshots, not business/audit records. Stop old gateways before upgrade.
DELETE FROM authorization_outbox WHERE state_key LIKE 'auth:member:%';
