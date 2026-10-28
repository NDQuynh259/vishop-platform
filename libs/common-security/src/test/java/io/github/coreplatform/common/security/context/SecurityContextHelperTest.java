package io.github.coreplatform.common.security.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.coreplatform.common.domain.constants.SystemRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextHelperTest {

  // #region tearDown
  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  // #endregion

  // #region shouldExtractCurrentUserWhenAuthenticated
  @Test
  @DisplayName("Should extract current user and tenant details when authenticated")
  void shouldExtractCurrentUserWhenAuthenticated() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    CommonUserDetails user =
        new CommonUserDetails(userId, tenantId, SystemRole.USER.name(), null, Set.of(), List.of());

    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

    assertThat(SecurityContextHelper.getCurrentUser()).contains(user);
    assertThat(SecurityContextHelper.requiredCurrentUser()).isEqualTo(user);
    assertThat(SecurityContextHelper.getCurrentUserId()).contains(userId);
    assertThat(SecurityContextHelper.requiredCurrentUserId()).isEqualTo(userId);
    assertThat(SecurityContextHelper.getCurrentTenantId()).contains(tenantId);
    assertThat(SecurityContextHelper.requiredCurrentTenantId()).isEqualTo(tenantId);
  }

  // #endregion

  // #region shouldFallbackToTenantContextWhenNotAuthenticated
  @Test
  @DisplayName("Should fall back to TenantContext when user is not authenticated")
  void shouldFallbackToTenantContextWhenNotAuthenticated() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(tenantId);

    assertThat(SecurityContextHelper.getCurrentUser()).isEmpty();
    assertThat(SecurityContextHelper.getCurrentTenantId()).contains(tenantId);
    assertThat(SecurityContextHelper.requiredCurrentTenantId()).isEqualTo(tenantId);
  }

  // #endregion

  // #region shouldThrowExceptionWhenRequiredContextMissing
  @Test
  @DisplayName("Should throw IllegalStateException when required user or tenant is absent")
  void shouldThrowExceptionWhenRequiredContextMissing() {
    assertThatThrownBy(SecurityContextHelper::requiredCurrentUser)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Authenticated user context is required");

    assertThatThrownBy(SecurityContextHelper::requiredCurrentUserId)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Authenticated user context is required");

    assertThatThrownBy(SecurityContextHelper::requiredCurrentTenantId)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Tenant context is required");
  }
  // #endregion
}
