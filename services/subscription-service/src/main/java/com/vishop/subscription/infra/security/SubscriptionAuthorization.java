package com.vishop.subscription.infra.security;

import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.jwt.VerifiedAccessToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("subscriptionAuthorization")
public class SubscriptionAuthorization {
  public boolean isCms(Authentication auth) {
    return auth != null
        && auth.isAuthenticated()
        && auth.getPrincipal() instanceof VerifiedAccessToken actor
        && SystemRole.PLATFORM_ADMIN.name().equals(actor.systemRole());
  }

  public boolean hasTenant(Authentication auth) {
    return auth != null
        && auth.isAuthenticated()
        && auth.getPrincipal() instanceof VerifiedAccessToken actor
        && actor.tenantId() != null;
  }

  public static AuthenticatedActor actor(VerifiedAccessToken token) {
    return new AuthenticatedActor(
        token.userId(), token.tenantId(), token.systemRole(), token.permissions());
  }
}
