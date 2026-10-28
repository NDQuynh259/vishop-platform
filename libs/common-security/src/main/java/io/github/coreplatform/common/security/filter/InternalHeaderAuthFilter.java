package io.github.coreplatform.common.security.filter;

import io.github.coreplatform.common.security.context.CommonUserDetails;
import io.github.coreplatform.common.security.context.TenantContext;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.github.coreplatform.common.security.jwt.VerifiedAccessToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Verifies signed tokens; identity headers alone confer no authority. */
public class InternalHeaderAuthFilter extends OncePerRequestFilter {
  private final JwtTokenProvider tokens;

  public InternalHeaderAuthFilter(JwtTokenProvider tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
    try {
      String token = extractToken(request);
      if (token != null) {
        CommonUserDetails principal;
        try {
          var access = VerifiedAccessToken.verify(tokens, token);
          var authorities = access.permissions().stream().map(SimpleGrantedAuthority::new).toList();
          principal =
              new CommonUserDetails(
                  access.userId(),
                  access.tenantId(),
                  access.systemRole(),
                  access.tenantRole(),
                  Set.of(),
                  authorities);
        } catch (RuntimeException invalidToken) {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid authentication token");
          return;
        }
        TenantContext.set(principal.tenantId());
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities()));
      }
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clear();
    }
  }

  private static String extractToken(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization != null) {
      return authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : "";
    }
    return null;
  }
}
