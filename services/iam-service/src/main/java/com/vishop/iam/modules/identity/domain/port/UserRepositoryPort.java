package com.vishop.iam.modules.identity.domain.port;

import com.vishop.iam.modules.identity.domain.entity.User;
import io.github.coreplatform.common.domain.constants.SystemRole;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
  Optional<User> user(UUID id);

  Optional<User> lockByEmail(String email);

  Optional<User> lockUser(UUID id);

  User create(String email, String passwordHash);

  void rememberTenant(UUID userId, UUID tenantId);

  void updateUserStatus(UUID user, String status);

  void lockSystemRoles();

  int activeAdmins();

  void updateSystemRole(UUID user, SystemRole role);
}
