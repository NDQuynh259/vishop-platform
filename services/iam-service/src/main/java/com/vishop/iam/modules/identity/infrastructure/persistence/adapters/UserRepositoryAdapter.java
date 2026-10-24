package com.vishop.iam.modules.identity.infrastructure.persistence.adapters;

import static com.vishop.iam.modules.identity.infrastructure.persistence.repositories.IdentityJdbcMappings.*;
import static com.vishop.iam.shared.infrastructure.persistence.JdbcValues.*;

import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.modules.identity.domain.valueobject.SystemRole;
import io.github.coreplatform.common.domain.UuidV7;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryAdapter implements UserRepositoryPort {
  private final JdbcTemplate db;

  public UserRepositoryAdapter(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public Optional<User> user(UUID id) {
    return first(db.query("SELECT * FROM users WHERE id=?", USER, id));
  }

  @Override
  public Optional<User> userByEmail(String email) {
    return first(db.query("SELECT * FROM users WHERE lower(email)=lower(?)", USER, email));
  }

  @Override
  public User createUser(String email, String hash, String name) {
    UUID id = UuidV7.nextId();
    db.update(
        "INSERT INTO users(id,email,password_hash,full_name) VALUES (?,?,?,?)",
        id,
        email,
        hash,
        name);
    return user(id).orElseThrow();
  }

  @Override
  public void updateUserStatus(UUID user, String status) {
    db.update(
        "UPDATE users SET status=?,updated_at=now(),version=version+1 WHERE id=?", status, user);
  }

  @Override
  public void lockSystemRoles() {
    db.execute("SELECT pg_advisory_xact_lock(739141002)");
  }

  @Override
  public int activeAdmins() {
    return db.queryForObject(
        "SELECT count(*) FROM users WHERE system_role='PLATFORM_ADMIN' AND status='ACTIVE'",
        Integer.class);
  }

  @Override
  public void updateSystemRole(UUID user, SystemRole role) {
    db.update(
        "UPDATE users SET system_role=?,updated_at=now(),version=version+1 WHERE id=?",
        role.name(),
        user);
  }
}
