package com.vishop.iam.modules.identity.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import io.github.coreplatform.common.security.constants.AuthCookies;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;

class AuthenticationCookiesTest {
  @Test
  void refreshCookieLifetimeNeverRestartsTheAbsoluteRefreshWindow() {
    var result =
        new AuthResponse(
            "access", "refresh", null, null, null, Set.of(), Instant.now().plusSeconds(120));
    var writer = new AuthenticationCookies(true, "Strict", 900000);
    var cookies = writer.issue(result).get("Set-Cookie").stream().map(MockCookie::parse).toList();
    assertThat(cookies)
        .hasSize(2)
        .allSatisfy(
            cookie -> {
              assertThat(cookie.getSecure()).isTrue();
              assertThat(cookie.isHttpOnly()).isTrue();
              assertThat(cookie.getSameSite()).isEqualTo("Strict");
            });
    var refresh =
        cookies.stream()
            .filter(c -> c.getName().equals(AuthCookies.REFRESH_TOKEN))
            .findFirst()
            .orElseThrow();
    assertThat(refresh.getMaxAge()).isPositive().isLessThanOrEqualTo(120);
    assertThat(refresh.getPath()).isEqualTo(AuthCookies.REFRESH_PATH);
  }

  @Test
  void crossSiteCookiesRequireSecureTransport() {
    assertThatThrownBy(() -> new AuthenticationCookies(false, "None", 900000))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
