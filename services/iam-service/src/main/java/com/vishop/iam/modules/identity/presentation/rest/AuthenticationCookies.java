package com.vishop.iam.modules.identity.presentation.rest;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.constants.AuthCookies;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationCookies {
  private final boolean secure;
  private final String sameSite;
  private final Duration accessLifetime;

  public AuthenticationCookies(
      @Value("${vishop.security.cookies.secure:false}") boolean secure,
      @Value("${vishop.security.cookies.same-site:Lax}") String sameSite,
      @Value("${vishop.security.jwt.access-expiration-ms:900000}") long accessExpirationMs) {
    if (!Set.of("Lax", "Strict", "None").contains(sameSite) || ("None".equals(sameSite) && !secure))
      throw new IllegalArgumentException(
          "SameSite must be Lax/Strict, or None with Secure cookies");
    this.secure = secure;
    this.sameSite = sameSite;
    this.accessLifetime = Duration.ofMillis(accessExpirationMs);
  }

  public HttpHeaders issue(AuthResponse result) {
    var headers = new HttpHeaders();
    Duration remaining = Duration.between(Instant.now(), result.refreshExpiresAt());
    add(
        headers,
        AuthCookies.ACCESS_TOKEN,
        result.accessToken(),
        AuthCookies.ACCESS_PATH,
        accessLifetime);
    add(
        headers,
        AuthCookies.REFRESH_TOKEN,
        result.refreshToken(),
        AuthCookies.REFRESH_PATH,
        remaining.isNegative() ? Duration.ZERO : remaining);
    return headers;
  }

  public HttpHeaders clear() {
    var headers = new HttpHeaders();
    add(headers, AuthCookies.ACCESS_TOKEN, "", AuthCookies.ACCESS_PATH, Duration.ZERO);
    add(headers, AuthCookies.REFRESH_TOKEN, "", AuthCookies.REFRESH_PATH, Duration.ZERO);
    return headers;
  }

  public String requireRefresh(String value) {
    if (value == null || value.isBlank())
      throw new UnauthorizedException("Refresh cookie is required");
    return value;
  }

  private void add(HttpHeaders headers, String name, String value, String path, Duration lifetime) {
    headers.add(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(path)
            .maxAge(lifetime)
            .build()
            .toString());
  }
}
