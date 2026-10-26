package com.vishop.iam.modules.identity.application.api;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.util.UUID;

/** Public in-process identity contract; implementation and persistence stay inside this module. */
public interface IdentityApi {
  UserView requireActive(UUID userId);

  void requireCms(AuthenticatedUser actor);

  boolean isActive(UUID userId);
}
