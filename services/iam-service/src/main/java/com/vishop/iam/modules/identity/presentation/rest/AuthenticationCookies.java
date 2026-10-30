package com.vishop.iam.modules.identity.presentation.rest;

import com.vishop.iam.modules.identity.application.port.AuthTokens.Issued;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.constants.AuthCookies;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

/** Credential chỉ đi qua HttpOnly cookie; JSON response không chứa raw access/refresh token. */
@Component
public class AuthenticationCookies {
  private final boolean secure;
  private final String sameSite;

  public AuthenticationCookies(
      @Value("${vishop.security.cookies.secure:false}") boolean secure,
      @Value("${vishop.security.cookies.same-site:Lax}") String sameSite) {
    if (!java.util.Set.of("Lax", "Strict", "None").contains(sameSite)
        || ("None".equals(sameSite) && !secure))
      throw new IllegalArgumentException("Invalid cookie SameSite/Secure configuration");
    this.secure = secure;
    this.sameSite = sameSite;
  }

  public HttpHeaders issue(Issued tokens) {
    var headers = new HttpHeaders();
    add(
        headers,
        AuthCookies.ACCESS_TOKEN,
        tokens.accessToken(),
        AuthCookies.ACCESS_PATH,
        Math.max(0, Duration.between(Instant.now(), tokens.accessExpiresAt()).getSeconds()));
    add(
        headers,
        AuthCookies.REFRESH_TOKEN,
        tokens.refreshToken(),
        AuthCookies.REFRESH_PATH,
        Math.max(0, Duration.between(Instant.now(), tokens.refreshExpiresAt()).getSeconds()));
    return headers;
  }

  public HttpHeaders clear() {
    var headers = new HttpHeaders();
    add(headers, AuthCookies.ACCESS_TOKEN, "", AuthCookies.ACCESS_PATH, 0);
    add(headers, AuthCookies.REFRESH_TOKEN, "", AuthCookies.REFRESH_PATH, 0);
    return headers;
  }

  public String read(HttpServletRequest request, String name, boolean required) {
    var matches =
        request.getCookies() == null
            ? java.util.List.<jakarta.servlet.http.Cookie>of()
            : Arrays.stream(request.getCookies()).filter(c -> name.equals(c.getName())).toList();
    if (matches.size() > 1) throw new UnauthorizedException("Ambiguous authentication cookie");
    String value = matches.isEmpty() ? null : matches.getFirst().getValue();
    if (required && (value == null || value.isBlank()))
      throw new UnauthorizedException("Refresh cookie is required");
    return value == null || value.isBlank() ? null : value;
  }

  private void add(HttpHeaders headers, String name, String value, String path, long maxAge) {
    headers.add(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(name, value)
            .path(path)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .maxAge(maxAge)
            .build()
            .toString());
  }
}
