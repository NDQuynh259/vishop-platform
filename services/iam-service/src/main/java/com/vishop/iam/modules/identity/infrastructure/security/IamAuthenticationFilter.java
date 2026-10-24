package com.vishop.iam.modules.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.infra.security.IamPrincipal;
import com.vishop.iam.modules.identity.application.query.AuthenticateQuery;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.exception.DomainException;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.security.context.TenantContext;
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
  public static final String COOKIE = "vishop_session";
  private final QueryBus queries;
  private final ObjectMapper json;
  private final Set<String> origins;

  public IamAuthenticationFilter(QueryBus queries, ObjectMapper json, Set<String> origins) {
    this.queries = queries;
    this.json = json;
    this.origins = origins;
  }

  private boolean publicPath(String path) {
    return Set.of("/api/v1/auth/login", "/api/v1/auth/register", "/internal/auth/introspect")
            .contains(path)
        || path.startsWith("/actuator/")
        || path.startsWith("/swagger-ui")
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
      if (request.getMethod().equals("OPTIONS") || publicPath(request.getRequestURI())) {
        chain.doFilter(request, response);
        return;
      }
      String authorization = request.getHeader("Authorization");
      boolean bearer = authorization != null;
      String credential = null;
      if (bearer)
        credential = authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : "";
      else if (request.getCookies() != null)
        for (Cookie cookie : request.getCookies())
          if (cookie.getName().equals(COOKIE)) credential = cookie.getValue();
      if (credential == null || credential.isBlank()) {
        error(response, 401, "Authentication is required");
        return;
      }
      if (!bearer
          && !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
          && origin == null) {
        error(response, 403, "Cookie-authenticated writes require an allowed Origin");
        return;
      }
      IamPrincipal actor;
      try {
        actor = new IamPrincipal(queries.dispatch(new AuthenticateQuery(credential, bearer)));
      } catch (DomainException failure) {
        error(response, failure instanceof ForbiddenException ? 403 : 401, failure.getMessage());
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
