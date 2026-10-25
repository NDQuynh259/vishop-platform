package io.github.coreplatform.common.security.filter;

import io.github.coreplatform.common.security.context.CommonUserDetails;
import io.github.coreplatform.common.security.context.TenantContext;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
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
          var claims = tokens.parseClaims(token);
          if (claims.getExpiration() == null) {
            throw new IllegalArgumentException("Token expiration is required");
          }
          if (!"iam-service".equals(claims.getIssuer())
              || claims.getAudience() == null
              || !claims.getAudience().contains("vishop-api")
              || !"access".equals(claims.get("token_use"))) {
            throw new IllegalArgumentException("An IAM access token is required");
          }
          UUID userId = UUID.fromString(claims.getSubject());
          UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
          var authorities = new ArrayList<GrantedAuthority>();
          addAuthorities(authorities, claims.get("roles"), true);
          addAuthorities(authorities, claims.get("permissions"), false);
          String systemRole = claims.get("systemRole", String.class);
          if (!Set.of("USER", "PLATFORM_ADMIN").contains(systemRole)) {
            throw new IllegalArgumentException("Invalid system role");
          }
          authorities.add(new SimpleGrantedAuthority("SYSTEM_" + systemRole));
          principal = new CommonUserDetails(userId, tenantId, Set.of(), authorities);
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

  private static void addAuthorities(
      ArrayList<GrantedAuthority> target, Object values, boolean roles) {
    if (!(values instanceof java.util.List<?> list)) return;
    for (Object value : list) {
      if (!(value instanceof String name) || name.isBlank()) {
        throw new IllegalArgumentException("Invalid authority claim");
      }
      target.add(new SimpleGrantedAuthority(roles ? "TENANT_ROLE_" + name : name));
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
