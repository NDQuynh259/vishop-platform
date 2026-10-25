package com.vishop.iam.modules.identity.application.mapper;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.domain.entity.User;

public final class UserViewMapper {
  private UserViewMapper() {}

  public static UserView from(User user) {
    return new UserView(
        user.id(),
        user.email(),
        user.fullName(),
        user.systemRole().name(),
        user.status(),
        user.createdAt());
  }
}
