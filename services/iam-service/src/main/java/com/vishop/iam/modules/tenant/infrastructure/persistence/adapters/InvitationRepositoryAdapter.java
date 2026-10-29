package com.vishop.iam.modules.tenant.infrastructure.persistence.adapters;

import com.vishop.iam.modules.tenant.domain.entity.Invitation;
import com.vishop.iam.modules.tenant.domain.port.InvitationRepositoryPort;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.InvitationJpaEntity;
import com.vishop.iam.modules.tenant.infrastructure.persistence.repositories.SpringDataInvitationRepository;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepositoryAdapter implements InvitationRepositoryPort {
  private final SpringDataInvitationRepository invitations;

  public InvitationRepositoryAdapter(SpringDataInvitationRepository invitations) {
    this.invitations = invitations;
  }

  public Invitation invite(UUID tenant, String email, UUID role, UUID inviter, String hash) {
    invitations.expire(tenant, email);
    var id = UuidV7.nextId();
    invitations.insert(id, tenant, email, role, inviter, hash);
    return invitations.findById(id).orElseThrow().toDomain();
  }

  public Optional<Invitation> invitation(String hash) {
    return invitations.active(hash).map(InvitationJpaEntity::toDomain);
  }

  public void acceptInvitation(UUID id, UUID user) {
    if (invitations.accept(id, user) != 1)
      throw new BadRequestException("Invitation is no longer valid");
  }
}
