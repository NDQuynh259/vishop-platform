package com.vishop.iam.infra.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.iam.modules.identity.application.service.IdentityAuthorizationService;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.modules.tenant.application.service.TenantAuthorizationService;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class StatelessAuthorizationTest {
  @Test
  void cmsAuthorizationUsesSignedRoleWithoutReadingUserRepository() {
    var users = mock(UserRepositoryPort.class);
    var authorization = new IdentityAuthorizationService(users);
    var admin = new AuthenticatedUser(UUID.randomUUID(), null, "PLATFORM_ADMIN", null, Set.of());
    var user = new AuthenticatedUser(UUID.randomUUID(), null, "USER", null, Set.of());
    authorization.cms(admin);
    assertThatThrownBy(() -> authorization.cms(user)).isInstanceOf(ForbiddenException.class);
    verifyNoInteractions(users);

    var security = new IamAuthorization();
    var principal = new IamPrincipal(admin);
    var authenticated =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    assertThat(security.canAccessCms(authenticated)).isTrue();
    assertThat(security.canAccessCms(new UsernamePasswordAuthenticationToken(principal, null)))
        .isFalse();
    assertThat(security.canAccessCms(null)).isFalse();
  }

  @Test
  void tenantAuthorizationRequiresMatchingTenantAndClaimPermission() {
    UUID tenant = UUID.randomUUID();
    var actor =
        new AuthenticatedUser(
            UUID.randomUUID(), tenant, "USER", "OPERATIONS", Set.of("tenant.view"));
    var authorization = new TenantAuthorizationService();
    assertThat(authorization.access(actor, tenant, "tenant.view").permissions())
        .containsExactly("tenant.view");
    assertThatThrownBy(() -> authorization.access(actor, UUID.randomUUID(), "tenant.view"))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> authorization.access(actor, tenant, "tenant.manage"))
        .isInstanceOf(ForbiddenException.class);
    var admin =
        new AuthenticatedUser(
            UUID.randomUUID(), null, "PLATFORM_ADMIN", null, Set.of("tenant.view"));
    assertThatThrownBy(() -> authorization.access(admin, tenant, "tenant.view"))
        .isInstanceOf(ForbiddenException.class);
  }
}
