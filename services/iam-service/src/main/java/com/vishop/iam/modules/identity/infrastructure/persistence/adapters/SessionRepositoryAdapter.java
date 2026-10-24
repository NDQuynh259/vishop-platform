package com.vishop.iam.modules.identity.infrastructure.persistence.adapters;

import com.vishop.iam.modules.identity.domain.entity.Session;
import com.vishop.iam.modules.identity.domain.port.SessionRepositoryPort;
import com.vishop.iam.modules.identity.infrastructure.persistence.entities.SessionJpaEntity;
import com.vishop.iam.modules.identity.infrastructure.persistence.repositories.SpringDataSessionRepository;
import io.github.coreplatform.common.domain.UuidV7;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepositoryAdapter implements SessionRepositoryPort {
  private final SpringDataSessionRepository sessions;

  public SessionRepositoryAdapter(SpringDataSessionRepository sessions) {
    this.sessions = sessions;
  }

  public Session createSession(UUID user, String hash, long seconds) {
    var id = UuidV7.nextId();
    sessions.insert(id, user, hash, seconds);
    return session(id).orElseThrow();
  }

  public Optional<Session> session(UUID id) {
    return sessions.activeById(id).map(SessionJpaEntity::toDomain);
  }

  public Optional<Session> sessionByHash(String hash) {
    return sessions.activeByHash(hash).map(SessionJpaEntity::toDomain);
  }

  public void selectTenant(UUID session, UUID user, UUID tenant) {
    sessions.selectTenant(session, user, tenant);
  }

  public void revokeSession(UUID session, UUID user) {
    sessions.revoke(session, user);
  }

  public void revokeAllSessions(UUID user) {
    sessions.revokeAll(user);
  }
}
