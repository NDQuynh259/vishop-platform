package com.vishop.iam.access.domain.ports;

import com.vishop.iam.access.domain.entities.Invitation;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepositoryPort {
  Invitation invite(UUID tenant, String email, UUID role, UUID inviter, String hash);

  Optional<Invitation> invitation(String hash);

  void acceptInvitation(UUID id, UUID user);
}
