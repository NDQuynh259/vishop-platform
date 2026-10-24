package io.github.coreplatform.common.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coreplatform.common.security.context.CommonUserDetails;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ShopSecurityTest {

  private final ShopSecurity shopSecurity = new ShopSecurity();

  // #region tearDown
  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // #endregion

  // #region shouldCheckShopAccessCorrectly
  @Test
  @DisplayName("Should return true when user has access to shop, false otherwise")
  void shouldCheckShopAccessCorrectly() {
    UUID shop1 = UUID.randomUUID();
    UUID shop2 = UUID.randomUUID();
    UUID otherShop = UUID.randomUUID();

    CommonUserDetails user =
        new CommonUserDetails(
            UUID.randomUUID(), UUID.randomUUID(), Set.of(shop1, shop2), List.of());

    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

    assertThat(shopSecurity.hasAccess(shop1)).isTrue();
    assertThat(shopSecurity.hasAccess(shop2)).isTrue();
    assertThat(shopSecurity.hasAccess(otherShop)).isFalse();
    assertThat(shopSecurity.hasAccess(null)).isFalse();
  }

  // #endregion

  // #region shouldReturnFalseWhenUnauthenticated
  @Test
  @DisplayName("Should return false when user is not authenticated")
  void shouldReturnFalseWhenUnauthenticated() {
    assertThat(shopSecurity.hasAccess(UUID.randomUUID())).isFalse();
    assertThat(shopSecurity.hasAccess(null)).isFalse();
  }
  // #endregion
}
