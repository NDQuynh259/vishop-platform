package com.vishop.iam.modules.identity.domain.port;

import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.valueobject.SystemRole;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
  Optional<User> user(UUID id);

  Optional<User> userByEmail(String email);

  User createUser(String email, String hash, String name);

  void updateUserStatus(UUID user, String status);

  void lockSystemRoles();

  int activeAdmins();

  void updateSystemRole(UUID user, SystemRole role);
}
