-- Membership changes revoke all previously issued credentials for the affected user.
-- The existing auth_user trigger publishes the new tokenVersion transactionally.
CREATE FUNCTION iam_membership_token_version() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        UPDATE users SET token_version=token_version+1 WHERE id=OLD.user_id;
    ELSIF (NEW.tenant_id,NEW.user_id,NEW.role_id,NEW.status,NEW.deleted_at)
          IS DISTINCT FROM (OLD.tenant_id,OLD.user_id,OLD.role_id,OLD.status,OLD.deleted_at) THEN
        UPDATE users SET token_version=token_version+1
          WHERE id=OLD.user_id OR id=NEW.user_id;
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER auth_member_token_version AFTER UPDATE OR DELETE ON tenant_members
    FOR EACH ROW EXECUTE FUNCTION iam_membership_token_version();
