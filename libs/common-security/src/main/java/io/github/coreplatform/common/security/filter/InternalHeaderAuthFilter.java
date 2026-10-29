package io.github.coreplatform.common.security.filter;

import io.github.coreplatform.common.security.context.CommonUserDetails;
import io.github.coreplatform.common.security.context.TenantContext;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Verifies signed tokens; identity headers alone confer no authority. */
public class InternalHeaderAuthFilter extends OncePerRequestFilter {
  private final JwtTokenProvider tokens;
  private final io.github.coreplatform.common.security.authentication.JwtAuthenticationConverter
      converter;

  public InternalHeaderAuthFilter(
      JwtTokenProvider tokens,
      io.github.coreplatform.common.security.authentication.JwtAuthenticationConverter converter) {
    this.tokens = tokens;
    this.converter = converter;
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
        org.springframework.security.core.Authentication authentication;
        try {
          authentication = converter.convert(tokens.verify(token));
          if (authentication == null || !authentication.isAuthenticated())
            throw new IllegalArgumentException("Invalid principal");
        } catch (RuntimeException invalidToken) {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid authentication token");
          return;
        }
        if (authentication.getPrincipal() instanceof CommonUserDetails principal)
          TenantContext.set(principal.tenantId());
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
