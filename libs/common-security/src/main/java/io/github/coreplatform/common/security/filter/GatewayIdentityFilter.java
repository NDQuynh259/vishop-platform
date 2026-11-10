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
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter chạy một lần mỗi request tại các downstream service để khôi phục danh tính và quyền từ
 * các internal header do API Gateway truyền xuống.
 */
@RequiredArgsConstructor
public final class GatewayIdentityFilter extends OncePerRequestFilter {
  private final GatewayRolePermissions roles;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // Xóa sạch context cũ trước khi xử lý để tránh rò rỉ dữ liệu khi thread được tái sử dụng
    SecurityContextHolder.clearContext();
    TenantContext.clear();
    try {
      GatewayPrincipal actor = null;
      try {
        // Đọc các internal header định danh từ API Gateway (X-User-Id, X-Tenant-Id, X-Role-Id, ...)
        var identity = GatewayIdentityHeaders.read(name -> Collections.list(request.getHeaders(name)));
        if (identity != null) {
          // Chặn technical user (00000000-...) gọi vào các endpoint nghiệp vụ yêu cầu user thực
          if (GatewayIdentityHeaders.TECHNICAL_USER.equals(identity.getUserId()))
            throw new UnauthorizedException("User identity required");

          // Phân giải permissions của role trong tenant (tra cứu từ cache/Redis); nếu chưa chọn
          // tenant thì bỏ qua
          var grant = identity.getTenantId() == null
              ? null
              : roles.resolve(identity.getTenantId(), identity.getRoleId());

          actor = new GatewayPrincipal(
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
        // Fail-closed: Lỗi hạ tầng (vd: mất kết nối Redis khi tra cứu quyền) -> trả 503 và chặn
        // request
        response.sendError(503);
        return;
      }
      if (actor != null) {
        // Nạp principal và authorities vào Spring SecurityContext để Controller và
        // @PreAuthorize/@RequirePermission sử dụng
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    actor,
                    null,
                    actor.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
        // Nạp tenantId vào ThreadLocal cho tầng dữ liệu / multi-tenancy
        if (actor.tenantId() != null)
          TenantContext.set(actor.tenantId());
      }
      chain.doFilter(request, response);
    } finally {
      // Luôn dọn dẹp context sau khi request hoàn tất để tránh rò rỉ sang request khác trong
      // thread pool
      SecurityContextHolder.clearContext();
      TenantContext.clear();
    }
  }
}
