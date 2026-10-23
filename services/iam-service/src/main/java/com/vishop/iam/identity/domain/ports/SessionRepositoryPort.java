package com.vishop.iam.identity.domain.ports;

import com.vishop.iam.identity.domain.entities.Session;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepositoryPort {
  Session createSession(UUID user, String tokenHash, long seconds);

  Optional<Session> session(UUID id);

  Optional<Session> sessionByHash(String hash);

  void selectTenant(UUID session, UUID user, UUID tenant);

  void revokeSession(UUID session, UUID user);

  void revokeAllSessions(UUID user);
}
