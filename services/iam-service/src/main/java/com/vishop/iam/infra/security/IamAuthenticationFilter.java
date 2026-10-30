package com.vishop.iam.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.github.coreplatform.common.security.context.TenantContext;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class IamAuthenticationFilter extends OncePerRequestFilter {
  private final GatewayContextCodec identities;
  private final ObjectMapper json;
  private final Set<String> origins;

  public IamAuthenticationFilter(
      GatewayContextCodec identities, ObjectMapper json, Set<String> origins) {
    this.identities = identities;
    this.json = json;
    this.origins = origins;
  }

  private boolean publicPath(String path) {
    return Set.of(
                ApiPaths.V1 + "/auth/login",
                ApiPaths.V1 + "/auth/register",
                ApiPaths.V1 + "/auth/refresh",
                "/.well-known/jwks.json")
            .contains(path)
        || path.startsWith("/actuator/")
        || path.startsWith("/v3/api-docs");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      String origin = request.getHeader("Origin");
      if (origin != null && !origins.contains(origin)) {
        error(response, 403, "Origin is not allowed");
        return;
      }
      if (AuthCookies.requiresRequestHeader(request.getMethod())
          && !AuthCookies.REQUEST_HEADER_VALUE.equals(
              request.getHeader(AuthCookies.REQUEST_HEADER))) {
        error(response, 403, "X-Requested-With: XMLHttpRequest is required");
        return;
      }
      if (request.getMethod().equals("OPTIONS") || publicPath(request.getRequestURI())) {
        chain.doFilter(request, response);
        return;
      }
      boolean logout = request.getRequestURI().equals(ApiPaths.V1 + "/auth/logout");
      String credential = request.getHeader(GatewayContextCodec.HEADER);
      if (credential == null || credential.isBlank()) {
        if (logout) chain.doFilter(request, response);
        else error(response, 401, "Access cookie is required");
        return;
      }
      IamPrincipal actor;
      try {
        if (java.util.Collections.list(request.getHeaders(GatewayContextCodec.HEADER)).size() != 1)
          throw new IllegalArgumentException("Ambiguous gateway context");
        String target =
            request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        var c = identities.verify(credential, request.getMethod(), target);
        if (request.getRequestURI().startsWith("/internal/")
            && !c.userId().equals(new java.util.UUID(0, 0)))
          throw new IllegalArgumentException("Invalid internal caller");
        actor =
            new IamPrincipal(
                new com.vishop.iam.shared.application.dto.AuthenticatedUser(
                    c.userId(), c.tenantId(), c.systemRole(), c.tenantRole(), c.permissions()));
      } catch (IllegalArgumentException failure) {
        if (logout) chain.doFilter(request, response);
        else error(response, 401, "Invalid gateway context");
        return;
      }
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
      if (actor.tenantId() != null) TenantContext.set(actor.tenantId());
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      SecurityContextHolder.clearContext();
    }
  }

  private void error(HttpServletResponse response, int status, String detail) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    json.writeValue(
        response.getOutputStream(),
        Map.of(
            "type",
            status == 401 ? "/errors/unauthorized" : "/errors/forbidden",
            "title",
            status == 401 ? "Unauthorized" : "Forbidden",
            "status",
            status,
            "detail",
            detail));
  }
}
