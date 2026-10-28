package com.vishop.audit.infra.security;

import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.github.coreplatform.common.security.jwt.VerifiedAccessToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Uses signed IAM access claims; platform admins need not select a tenant to use CMS. */
final class AuditAccessTokenFilter extends OncePerRequestFilter {
  private final JwtTokenProvider tokens;

  AuditAccessTokenFilter(JwtTokenProvider tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    SecurityContextHolder.clearContext();
    try {
      String header = request.getHeader("Authorization");
      if (header != null) {
        VerifiedAccessToken actor;
        try {
          if (!header.startsWith("Bearer "))
            throw new IllegalArgumentException("Bearer token required");
          actor = VerifiedAccessToken.verify(tokens, header.substring(7).trim());
        } catch (RuntimeException invalid) {
          response.sendError(401, "Invalid authentication token");
          return;
        }
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    actor,
                    null,
                    actor.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
      }
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
