package com.vishop.iam.modules.identity.presentation.rest.dto;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.dto.UserView;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Public authentication response; credentials are delivered only through HttpOnly cookies. */
public record AuthenticationView(
    UserView user, UUID tenantId, List<String> roles, Set<String> permissions) {
  public static AuthenticationView from(AuthResponse result) {
    return new AuthenticationView(
        result.user(), result.tenantId(), result.roles(), result.permissions());
  }
}
