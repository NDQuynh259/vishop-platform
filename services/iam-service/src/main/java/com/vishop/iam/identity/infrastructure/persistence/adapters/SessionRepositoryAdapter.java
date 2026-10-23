package com.vishop.iam.identity.infrastructure.persistence.adapters;

import static com.vishop.iam.identity.infrastructure.persistence.repositories.IdentityJdbcMappings.*;
import static com.vishop.iam.shared.infrastructure.persistence.JdbcValues.*;

import com.vishop.iam.identity.domain.entities.Session;
import com.vishop.iam.identity.domain.ports.SessionRepositoryPort;
import io.github.coreplatform.common.domain.UuidV7;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepositoryAdapter implements SessionRepositoryPort {
  private final JdbcTemplate db;

  public SessionRepositoryAdapter(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public Session createSession(UUID user, String tokenHash, long seconds) {
    UUID id = UuidV7.nextId();
    db.update(
        "INSERT INTO sessions(id,user_id,token_hash,expires_at) VALUES (?,?,?,now()+(? * interval '1 second'))",
        id,
        user,
        tokenHash,
        seconds);
    return session(id).orElseThrow();
  }

  @Override
  public Optional<Session> session(UUID id) {
    return first(
        db.query(
            "SELECT * FROM sessions WHERE id=? AND revoked_at IS NULL AND expires_at>clock_timestamp()",
            SESSION,
            id));
  }

  @Override
  public Optional<Session> sessionByHash(String hash) {
    return first(
        db.query(
            "SELECT * FROM sessions WHERE token_hash=? AND revoked_at IS NULL AND expires_at>clock_timestamp()",
            SESSION,
            hash));
  }

  @Override
  public void selectTenant(UUID session, UUID user, UUID tenant) {
    db.update(
        "UPDATE sessions SET active_tenant_id=?,updated_at=now() WHERE id=? AND user_id=? AND revoked_at IS NULL",
        tenant,
        session,
        user);
  }

  @Override
  public void revokeSession(UUID session, UUID user) {
    db.update(
        "UPDATE sessions SET revoked_at=now(),updated_at=now() WHERE id=? AND user_id=? AND revoked_at IS NULL",
        session,
        user);
  }

  @Override
  public void revokeAllSessions(UUID user) {
    db.update(
        "UPDATE sessions SET revoked_at=clock_timestamp(),updated_at=clock_timestamp() "
            + "WHERE user_id=? AND revoked_at IS NULL",
        user);
  }
}
