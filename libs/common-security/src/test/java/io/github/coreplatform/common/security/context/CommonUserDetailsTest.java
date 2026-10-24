package io.github.coreplatform.common.security.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
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
    UUID shop1 = UUID.randomUUID();
    UUID shop2 = UUID.randomUUID();

    CommonUserDetails user =
        new CommonUserDetails(
            userId,
            tenantId,
            Set.of(shop1, shop2),
            List.of(
                new SimpleGrantedAuthority("ROLE_OWNER"),
                new SimpleGrantedAuthority("order.create")));

    assertThat(user.userId()).isEqualTo(userId);
    assertThat(user.tenantId()).isEqualTo(tenantId);
    assertThat(user.getUsername()).isEqualTo(userId.toString());
    assertThat(user.getPassword()).isEmpty();
    assertThat(user.isEnabled()).isTrue();
    assertThat(user.isAccountNonExpired()).isTrue();
    assertThat(user.isAccountNonLocked()).isTrue();
    assertThat(user.isCredentialsNonExpired()).isTrue();

    assertThat(user.hasShopAccess(shop1)).isTrue();
    assertThat(user.hasShopAccess(shop2)).isTrue();
    assertThat(user.hasShopAccess(UUID.randomUUID())).isFalse();
    assertThat(user.hasShopAccess(null)).isFalse();

    assertThat(user.hasAuthority("ROLE_OWNER")).isTrue();
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

    assertThatThrownBy(() -> new CommonUserDetails(null, validId, Set.of(), List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("userId must not be null");

    assertThatThrownBy(() -> new CommonUserDetails(validId, null, Set.of(), List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  // #endregion

  // #region shouldHandleNullCollectionsGracefully
  @Test
  @DisplayName("Should handle null collections gracefully by assigning empty immutable collections")
  void shouldHandleNullCollectionsGracefully() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    CommonUserDetails user = new CommonUserDetails(userId, tenantId, null, null);

    assertThat(user.accessibleShopIds()).isEmpty();
    assertThat(user.authorities()).isEmpty();
    assertThat(user.hasShopAccess(UUID.randomUUID())).isFalse();
    assertThat(user.hasAuthority("any.perm")).isFalse();
  }
  // #endregion
}
