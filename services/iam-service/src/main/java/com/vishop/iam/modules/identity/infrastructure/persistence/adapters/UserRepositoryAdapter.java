package com.vishop.iam.modules.identity.infrastructure.persistence.adapters;

import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.modules.identity.domain.valueobject.SystemRole;
import com.vishop.iam.modules.identity.infrastructure.persistence.entities.UserJpaEntity;
import com.vishop.iam.modules.identity.infrastructure.persistence.repositories.SpringDataUserRepository;
import io.github.coreplatform.common.domain.UuidV7;
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
    return users.findById(id).map(UserJpaEntity::toDomain);
  }

  public Optional<User> userByEmail(String email) {
    return users.byEmail(email).map(UserJpaEntity::toDomain);
  }

  public User createUser(String email, String hash) {
    return users.saveAndFlush(UserJpaEntity.create(UuidV7.nextId(), email, hash)).toDomain();
  }

  public void updateUserStatus(UUID user, String status) {
    users.updateStatus(user, status);
  }

  public void updateLastTenant(UUID user, UUID tenant) {
    users.updateLastTenant(user, tenant);
  }

  public void lockSystemRoles() {
    // Return a scalar instead of the PostgreSQL void type while retaining the transaction lock.
    entityManager
        .createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(739141002)")
        .getSingleResult();
  }

  public int activeAdmins() {
    return users.activeAdmins();
  }

  public void updateSystemRole(UUID user, SystemRole role) {
    users.updateSystemRole(user, role.name());
  }
}
