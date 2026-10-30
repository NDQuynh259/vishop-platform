package com.vishop.iam.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.modules.identity.application.api.IdentityApi;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.github.coreplatform.common.security.context.TenantContext;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Verify context gateway ở ranh giới HTTP, không tin X-User-Id/X-Tenant-Id client tự gửi. */
public class IamAuthenticationFilter extends OncePerRequestFilter {
  private final GatewayContextCodec codec;
  private final IdentityApi identity;
  private final ObjectMapper json;
  private final Set<String> origins;

  public IamAuthenticationFilter(
      GatewayContextCodec codec, IdentityApi identity, ObjectMapper json, Set<String> origins) {
    this.codec = codec;
    this.identity = identity;
    this.json = json;
    this.origins = origins;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      if (request.getHeader("Origin") != null && !origins.contains(request.getHeader("Origin"))) {
        error(response, 403, "origin_not_allowed");
        return;
      }
      if (AuthCookies.requiresRequestHeader(request.getMethod())
          && !AuthCookies.REQUEST_HEADER_VALUE.equals(
              request.getHeader(AuthCookies.REQUEST_HEADER))) {
        error(response, 403, "csrf_request_header_required");
        return;
      }
      String path = request.getRequestURI();
      boolean logout = path.equals(ApiPaths.V1 + "/auth/logout");
      if (request.getMethod().equals("OPTIONS") || publicPath(path)) {
        chain.doFilter(request, response);
        return;
      }
      String signed = request.getHeader(GatewayContextCodec.HEADER);
      if (signed == null || signed.isBlank()) {
        if (logout) chain.doFilter(request, response);
        else error(response, 401, "gateway_context_required");
        return;
      }
      AuthenticatedUser actor;
      try {
        if (java.util.Collections.list(request.getHeaders(GatewayContextCodec.HEADER)).size() != 1)
          throw new IllegalArgumentException("Ambiguous gateway context");
        String target =
            path + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        var context = codec.verifyContext(signed, request.getMethod(), target);
        boolean technical = context.userId().equals(new UUID(0, 0));
        if (path.startsWith("/internal/")) {
          if (!technical) throw new UnauthorizedException("Internal gateway caller required");
          actor = new AuthenticatedUser(context.userId(), null, "USER", null, Set.of());
        } else {
          if (technical) throw new UnauthorizedException("User context required");
          // IAM đọc DB để xác nhận user, session, version và membership vẫn hợp lệ.
          actor =
              identity.resolveContext(
                  new AuthenticatedUser(
                      context.userId(),
                      context.tenantId(),
                      context.systemRole(),
                      context.tenantRole(),
                      context.permissions(),
                      context.sessionId()),
                  context.roleId());
        }
      } catch (UnauthorizedException | IllegalArgumentException invalid) {
        if (logout) chain.doFilter(request, response);
        else error(response, 401, "invalid_authentication");
        return;
      } catch (ForbiddenException denied) {
        error(response, 403, "access_not_active");
        return;
      } catch (RuntimeException unavailable) {
        error(response, 503, "authentication_unavailable");
        return;
      }
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  actor,
                  null,
                  actor.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
      if (actor.tenantId() != null) TenantContext.set(actor.tenantId());
      chain.doFilter(request, response);
    } finally {
      // Servlet thread được tái sử dụng: không để tenant/principal của request trước lọt sang
      // request sau.
      TenantContext.clear();
      SecurityContextHolder.clearContext();
    }
  }

  private boolean publicPath(String path) {
    return Set.of(
                ApiPaths.V1 + "/auth/login",
                ApiPaths.V1 + "/auth/register",
                ApiPaths.V1 + "/auth/refresh")
            .contains(path)
        || path.startsWith("/actuator/")
        || path.startsWith("/v3/api-docs")
        || path.equals("/error");
  }

  private void error(HttpServletResponse response, int status, String code) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    json.writeValue(
        response.getOutputStream(),
        java.util.Map.of(
            "status",
            status,
            "code",
            code,
            "title",
            status == 503
                ? "Authentication unavailable"
                : status == 403 ? "Forbidden" : "Unauthorized"));
  }
}
