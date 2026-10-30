package com.vishop.iam.modules.identity.infrastructure.persistence.adapters;

import com.vishop.iam.modules.identity.domain.entity.AuthSession;
import com.vishop.iam.modules.identity.domain.port.AuthSessionRepositoryPort;
import com.vishop.iam.modules.identity.infrastructure.persistence.entities.AuthSessionJpaEntity;
import com.vishop.iam.modules.identity.infrastructure.persistence.repositories.SpringDataAuthSessionRepository;
import io.github.coreplatform.common.domain.UuidV7;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AuthSessionRepositoryAdapter implements AuthSessionRepositoryPort {
  private final SpringDataAuthSessionRepository sessions;

  public AuthSessionRepositoryAdapter(SpringDataAuthSessionRepository sessions) {
    this.sessions = sessions;
  }

  public AuthSession create(UUID userId, long version, Instant expiresAt) {
    return sessions
        .saveAndFlush(AuthSessionJpaEntity.create(UuidV7.nextId(), userId, version, expiresAt))
        .toDomain();
  }

  public Optional<AuthSession> find(UUID id) {
    return sessions.findById(id).map(AuthSessionJpaEntity::toDomain);
  }

  public boolean revoke(UUID id, UUID userId) {
    return sessions.revoke(id, userId) == 1;
  }
}
