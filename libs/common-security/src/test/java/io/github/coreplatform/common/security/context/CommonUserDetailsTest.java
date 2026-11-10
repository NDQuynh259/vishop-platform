package io.github.coreplatform.common.security.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.coreplatform.common.domain.constants.SystemRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CommonUserDetailsTest {

  // #region shouldCreateCommonUserDetailsSuccessfully
  @Test
  @DisplayName("Should create CommonUserDetails with all fields and check access")
  void shouldCreateCommonUserDetailsSuccessfully() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    CommonUserDetails user =
        new CommonUserDetails(
            userId,
            tenantId,
            SystemRole.USER.name(),
            "OWNER",
            List.of(new SimpleGrantedAuthority("order.create")));

    assertThat(user.userId()).isEqualTo(userId);
    assertThat(user.tenantId()).isEqualTo(tenantId);
    assertThat(user.getUsername()).isEqualTo(userId.toString());
    assertThat(user.getPassword()).isEmpty();
    assertThat(user.isEnabled()).isTrue();
    assertThat(user.isAccountNonExpired()).isTrue();
    assertThat(user.isAccountNonLocked()).isTrue();
    assertThat(user.isCredentialsNonExpired()).isTrue();

    assertThat(user.tenantRole()).isEqualTo("OWNER");
    assertThat(user.systemRole()).isEqualTo(SystemRole.USER.name());
    assertThat(user.hasAuthority("ROLE_OWNER")).isFalse();
    assertThat(user.hasAuthority("order.create")).isTrue();
    assertThat(user.hasAuthority("ORDER.CREATE")).isTrue(); // Case-insensitive
    assertThat(user.hasAuthority("product.delete")).isFalse();
    assertThat(user.hasAuthority(null)).isFalse();
  }

  // #endregion

  // #region shouldThrowExceptionWhenRequiredFieldsAreNull
  @Test
  @DisplayName("Should throw NullPointerException when userId or tenantId is null")
  void shouldThrowExceptionWhenRequiredFieldsAreNull() {
    UUID validId = UUID.randomUUID();

    assertThatThrownBy(
            () -> new CommonUserDetails(null, validId, SystemRole.USER.name(), null, List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("userId must not be null");

    assertThatThrownBy(
            () -> new CommonUserDetails(validId, null, SystemRole.USER.name(), null, List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  // #endregion

  // #region shouldHandleNullCollectionsGracefully
  @Test
  @DisplayName(
      "Should handle null authorities gracefully by assigning an empty immutable collection")
  void shouldHandleNullCollectionsGracefully() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    CommonUserDetails user =
        new CommonUserDetails(userId, tenantId, SystemRole.USER.name(), null, null);

    assertThat(user.authorities()).isEmpty();
    assertThat(user.hasAuthority("any.perm")).isFalse();
  }
  // #endregion
}
