package com.vishop.audit.infra.security;

import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Accepts only gateway-authenticated context; never verifies the original user JWT. */
final class AuditAccessTokenFilter extends OncePerRequestFilter {
  private final GatewayContextCodec tokens;

  AuditAccessTokenFilter(GatewayContextCodec tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    SecurityContextHolder.clearContext();
    try {
      String header = request.getHeader(GatewayContextCodec.HEADER);
      if (header != null) {
        GatewayPrincipal actor;
        try {
          if (java.util.Collections.list(request.getHeaders(GatewayContextCodec.HEADER)).size()
              != 1) throw new IllegalArgumentException("Ambiguous gateway context");
          String target =
              request.getRequestURI()
                  + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
          actor = tokens.verify(header, request.getMethod(), target);
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
