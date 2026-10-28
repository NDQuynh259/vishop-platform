package com.vishop.iam.modules.identity.domain.port;

import com.vishop.iam.modules.identity.domain.entity.User;
import io.github.coreplatform.common.domain.constants.SystemRole;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
  Optional<User> user(UUID id);

  Optional<User> userByEmail(String email);

  User createUser(String email, String hash);

  void updateUserStatus(UUID user, String status);

  void updateLastTenant(UUID user, UUID tenant);

  void lockSystemRoles();

  int activeAdmins();

  void updateSystemRole(UUID user, SystemRole role);
}
