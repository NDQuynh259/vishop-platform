package com.vishop.iam.modules.access.domain.port;

import com.vishop.iam.modules.access.domain.entity.Invitation;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepositoryPort {
  Invitation invite(UUID tenant, String email, UUID role, UUID inviter, String hash);

  Optional<Invitation> invitation(String hash);

  void acceptInvitation(UUID id, UUID user);
}
