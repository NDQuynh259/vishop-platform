package com.vishop.iam.modules.identity.infrastructure.persistence.adapters;

import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.modules.identity.infrastructure.persistence.entities.UserJpaEntity;
import com.vishop.iam.modules.identity.infrastructure.persistence.repositories.SpringDataUserRepository;
import io.github.coreplatform.common.domain.constants.SystemRole;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryAdapter implements UserRepositoryPort {
  private final SpringDataUserRepository users;
  private final EntityManager entityManager;

  public UserRepositoryAdapter(SpringDataUserRepository users, EntityManager entityManager) {
    this.users = users;
    this.entityManager = entityManager;
  }

  public Optional<User> user(UUID id) {
    return users.activeRow(id).map(UserJpaEntity::toDomain);
  }

  public Optional<User> lockByEmail(String email) {
    return users.lockByEmail(email).map(UserJpaEntity::toDomain);
  }

  public Optional<User> lockUser(UUID id) {
    return users.lockUser(id).map(UserJpaEntity::toDomain);
  }

  public User create(String email, String hash) {
    return users
        .saveAndFlush(
            UserJpaEntity.create(io.github.coreplatform.common.domain.UuidV7.nextId(), email, hash))
        .toDomain();
  }

  public void rememberTenant(UUID userId, UUID tenantId) {
    users.rememberTenant(userId, tenantId);
  }

  public void updateUserStatus(UUID user, String status) {
    users.updateStatus(user, status);
  }

  public void lockSystemRoles() {
    // Return a scalar instead of the PostgreSQL void type while retaining the transaction lock.
    entityManager
        .createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(739141002)")
        .getSingleResult();
  }

  public int activeAdmins() {
    return users.activeAdmins(SystemRole.PLATFORM_ADMIN.name());
  }

  public void updateSystemRole(UUID user, SystemRole role) {
    users.updateSystemRole(user, role.name());
  }
}
