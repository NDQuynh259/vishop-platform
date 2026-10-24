package com.vishop.iam.modules.access.infrastructure.persistence.adapters;

import static com.vishop.iam.modules.access.infrastructure.persistence.repositories.AccessJdbcMappings.*;
import static com.vishop.iam.shared.infrastructure.persistence.JdbcValues.*;

import com.vishop.iam.modules.access.domain.entity.Invitation;
import com.vishop.iam.modules.access.domain.port.InvitationRepositoryPort;
import io.github.coreplatform.common.domain.UuidV7;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepositoryAdapter implements InvitationRepositoryPort {
  private final JdbcTemplate db;

  public InvitationRepositoryAdapter(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public Invitation invite(UUID tenant, String email, UUID role, UUID inviter, String hash) {
    db.update(
        "UPDATE tenant_invitations SET status='EXPIRED',updated_at=now(),version=version+1 "
            + "WHERE tenant_id=? AND lower(email)=lower(?) AND status='PENDING' AND expires_at<=clock_timestamp()",
        tenant,
        email);
    UUID id = UuidV7.nextId();
    db.update(
        "INSERT INTO tenant_invitations(id,tenant_id,email,role_id,invited_by,token_hash,expires_at) "
            + "VALUES (?,?,?,?,?,?,now()+interval '7 days')",
        id,
        tenant,
        email,
        role,
        inviter,
        hash);
    return db.queryForObject("SELECT * FROM tenant_invitations WHERE id=?", INVITATION, id);
  }

  @Override
  public Optional<Invitation> invitation(String hash) {
    return first(
        db.query(
            "SELECT * FROM tenant_invitations WHERE token_hash=? AND status='PENDING' AND expires_at>clock_timestamp()",
            INVITATION,
            hash));
  }

  @Override
  public void acceptInvitation(UUID id, UUID user) {
    int changed =
        db.update(
            "UPDATE tenant_invitations SET status='ACCEPTED',accepted_by=?,accepted_at=clock_timestamp(),"
                + "updated_at=now(),version=version+1 WHERE id=? AND status='PENDING' AND expires_at>clock_timestamp()",
            user,
            id);
    if (changed != 1)
      throw new io.github.coreplatform.common.domain.exception.BadRequestException(
          "Invitation is no longer valid");
  }
}
