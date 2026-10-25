package com.vishop.iam.modules.identity.application.api;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.shared.application.dto.IamActor;
import java.util.UUID;

public interface IdentityApi {
  UserView requireActive(UUID userId);

  void requireCms(IamActor actor);

  boolean isActive(UUID userId);
}
