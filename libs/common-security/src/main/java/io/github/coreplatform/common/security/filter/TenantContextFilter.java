package io.github.coreplatform.common.security.filter;

import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Restores tenant context from the authenticated principal. Unverified request headers never
 * establish tenant context.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

  public static final String TENANT_HEADER = SecurityHeaders.X_TENANT_ID;

  // #region doFilterInternal
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Only a verified principal may establish HTTP tenant context.
    io.github.coreplatform.common.security.context.SecurityContextHelper.getCurrentUser()
        .ifPresent(user -> TenantContext.set(user.tenantId()));

    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }
  // #endregion
}
