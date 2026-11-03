-- Coordinated IAM/gateway rollout: all gateways must enforce membership snapshots before
-- allowing traffic with this schema. Older gateways relied on the global token version.
DROP TRIGGER auth_member_token_version ON tenant_members;
DROP FUNCTION iam_membership_token_version();

-- Each membership has an independent Redis key and durable outbox revision.
CREATE FUNCTION iam_membership_authorization_refresh(tenant UUID, member_user UUID)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE key TEXT; state JSONB; revision BIGINT;
BEGIN
    key := 'auth:member:' || tenant || ':' || member_user;
    INSERT INTO authorization_outbox(state_key) VALUES(key) ON CONFLICT DO NOTHING;
    UPDATE authorization_outbox SET version=version+1,pending=true,updated_at=clock_timestamp()
      WHERE state_key=key RETURNING version INTO revision;
    SELECT jsonb_build_object('status',status,'roleId',role_id) INTO state
      FROM tenant_members
      WHERE tenant_id=tenant AND user_id=member_user AND deleted_at IS NULL;
    UPDATE authorization_outbox SET payload=
      coalesce(state,jsonb_build_object('status','INACTIVE'))
      || jsonb_build_object('key',key,'version',revision::TEXT,'userId',member_user)
      WHERE state_key=key;
END $$;

CREATE FUNCTION iam_membership_authorization_changed() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        PERFORM iam_membership_authorization_refresh(OLD.tenant_id,OLD.user_id);
    ELSE
        IF TG_OP='UPDATE' AND (NEW.tenant_id,NEW.user_id,NEW.role_id,NEW.status,NEW.deleted_at)
            IS NOT DISTINCT FROM (OLD.tenant_id,OLD.user_id,OLD.role_id,OLD.status,OLD.deleted_at)
            THEN RETURN NULL;
        END IF;
        PERFORM iam_membership_authorization_refresh(NEW.tenant_id,NEW.user_id);
        IF TG_OP='UPDATE' AND (NEW.tenant_id,NEW.user_id) IS DISTINCT FROM (OLD.tenant_id,OLD.user_id) THEN
            PERFORM iam_membership_authorization_refresh(OLD.tenant_id,OLD.user_id);
        END IF;
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER auth_member AFTER INSERT OR UPDATE OR DELETE ON tenant_members
    FOR EACH ROW EXECUTE FUNCTION iam_membership_authorization_changed();

SELECT iam_membership_authorization_refresh(tenant_id,user_id)
  FROM tenant_members ORDER BY tenant_id,user_id;
