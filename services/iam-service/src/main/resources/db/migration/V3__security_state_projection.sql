-- Durable security versions, independent of optimistic entity versions.
CREATE TABLE security_states (
    state_key VARCHAR(100) PRIMARY KEY,
    version BIGINT NOT NULL CHECK (version > 0),
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ,
    pending BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX idx_security_states_pending ON security_states(state_key) WHERE pending;
ALTER TABLE sessions ADD COLUMN context_version BIGINT NOT NULL DEFAULT 0;

CREATE FUNCTION iam_security_change(kind TEXT, object_id UUID, new_status TEXT, expiry TIMESTAMPTZ DEFAULT NULL)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE state security_states%ROWTYPE; event_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO security_states(state_key,version,status,expires_at,pending)
    VALUES (kind || ':' || object_id,1,
        COALESCE(new_status,(SELECT status FROM users WHERE id=object_id),'INACTIVE'),expiry,true)
    ON CONFLICT (state_key) DO UPDATE SET version=security_states.version+1,
        -- Grant changes only bump a version. Preserve a concurrent account suspension
        -- instead of copying a stale users.status snapshot after waiting for this row lock.
        status=CASE WHEN new_status IS NULL THEN security_states.status ELSE EXCLUDED.status END,
        expires_at=EXCLUDED.expires_at, pending=true
    RETURNING * INTO state;
    INSERT INTO outbox_events(id,event_type,aggregate_type,aggregate_id,payload,occurred_at)
    VALUES (event_id,'SecurityStateChanged','SecurityState',object_id,
        jsonb_build_object('eventId',event_id,'eventType','SecurityStateChanged','schemaVersion',1,
            'occurredAt',clock_timestamp(),'producer','iam-service','aggregateType','SecurityState',
            'aggregateId',object_id,'payload',jsonb_build_object('key',state.state_key,
            'version',state.version,'status',state.status,'expiresAt',state.expires_at)),clock_timestamp());
END $$;

CREATE FUNCTION iam_security_user() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' OR NEW.status IS DISTINCT FROM OLD.status
        OR NEW.system_role IS DISTINCT FROM OLD.system_role OR NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN
        PERFORM iam_security_change('user',NEW.id,NEW.status);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER security_user AFTER INSERT OR UPDATE ON users FOR EACH ROW EXECUTE FUNCTION iam_security_user();

CREATE FUNCTION iam_security_tenant() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' OR NEW.status IS DISTINCT FROM OLD.status THEN
        PERFORM iam_security_change('tenant',NEW.id,NEW.status);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER security_tenant AFTER INSERT OR UPDATE ON tenants FOR EACH ROW EXECUTE FUNCTION iam_security_tenant();

CREATE FUNCTION iam_security_session() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' OR NEW.revoked_at IS DISTINCT FROM OLD.revoked_at OR NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
        PERFORM iam_security_change('session',NEW.id,
            CASE WHEN NEW.revoked_at IS NOT NULL THEN 'REVOKED' ELSE 'ACTIVE' END,NEW.expires_at);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER security_session AFTER INSERT OR UPDATE ON sessions FOR EACH ROW EXECUTE FUNCTION iam_security_session();

CREATE FUNCTION iam_security_member() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE affected_user UUID;
BEGIN
    IF TG_OP='DELETE' THEN affected_user := OLD.user_id;
    ELSIF TG_OP='INSERT' OR NEW.role_id IS DISTINCT FROM OLD.role_id OR NEW.status IS DISTINCT FROM OLD.status THEN
        affected_user := NEW.user_id;
    ELSE RETURN NEW;
    END IF;
    PERFORM iam_security_change('user',u.id,NULL) FROM users u WHERE u.id=affected_user;
    RETURN NULL;
END $$;
CREATE TRIGGER security_member AFTER INSERT OR UPDATE OR DELETE ON tenant_members FOR EACH ROW EXECUTE FUNCTION iam_security_member();

CREATE FUNCTION iam_security_role() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE affected_role UUID;
BEGIN
    IF TG_TABLE_NAME='roles' THEN affected_role := NEW.id;
    ELSIF TG_OP='DELETE' THEN affected_role := OLD.role_id;
    ELSE affected_role := NEW.role_id;
    END IF;
    -- Stable ordering prevents different role updates taking user-state locks in opposite order.
    PERFORM iam_security_change('user',u.id,NULL) FROM users u
      WHERE u.id IN (SELECT m.user_id FROM tenant_members m WHERE m.role_id=affected_role)
      ORDER BY u.id;
    RETURN NULL;
END $$;
CREATE TRIGGER security_role AFTER UPDATE ON roles FOR EACH ROW EXECUTE FUNCTION iam_security_role();
CREATE TRIGGER security_permission AFTER INSERT OR UPDATE OR DELETE ON role_permissions FOR EACH ROW EXECUTE FUNCTION iam_security_role();

-- Existing sessions/users remain usable after migration, once the Redis projection is rebuilt.
INSERT INTO security_states(state_key,version,status)
SELECT 'user:' || id,1,status FROM users;
INSERT INTO security_states(state_key,version,status)
SELECT 'tenant:' || id,1,status FROM tenants;
INSERT INTO security_states(state_key,version,status,expires_at)
SELECT 'session:' || id,1,CASE WHEN revoked_at IS NULL THEN 'ACTIVE' ELSE 'REVOKED' END,expires_at FROM sessions;
