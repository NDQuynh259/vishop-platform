package io.github.coreplatform.common.security.filter;

import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.context.TenantContext;
import io.github.coreplatform.common.security.gateway.GatewayIdentityHeaders;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.security.gateway.GatewayRolePermissions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class GatewayIdentityFilter extends OncePerRequestFilter {
  private final GatewayRolePermissions roles;

  public GatewayIdentityFilter(GatewayRolePermissions roles) {
    this.roles = roles;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
    try {
      GatewayPrincipal actor = null;
      try {
        var identity =
            GatewayIdentityHeaders.read(name -> Collections.list(request.getHeaders(name)));
        if (identity != null) {
          if (GatewayIdentityHeaders.TECHNICAL_USER.equals(identity.getUserId()))
            throw new UnauthorizedException("User identity required");
          var grant =
              identity.getTenantId() == null
                  ? null
                  : roles.resolve(identity.getTenantId(), identity.getRoleId());
          actor =
              new GatewayPrincipal(
                  identity.getUserId(),
                  identity.getTenantId(),
                  identity.getSystemRole(),
                  grant == null ? null : grant.getRoleKey(),
                  grant == null ? Set.of() : grant.getPermissions(),
                  identity.getSessionId());
        }
      } catch (UnauthorizedException | IllegalArgumentException ex) {
        response.sendError(401);
        return;
      } catch (ForbiddenException ex) {
        response.sendError(403);
        return;
      } catch (RuntimeException ex) {
        response.sendError(503);
        return;
      }
      if (actor != null) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    actor,
                    null,
                    actor.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
        if (actor.tenantId() != null) TenantContext.set(actor.tenantId());
      }
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clear();
    }
  }
}
