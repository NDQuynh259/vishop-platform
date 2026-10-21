package io.github.coreplatform.common.security.filter;

import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.context.CommonUserDetails;
import io.github.coreplatform.common.security.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that intercepts incoming HTTP requests from the API Gateway,
 * parses trusted internal security headers, and populates SecurityContextHolder
 * and TenantContext. Guarantees ThreadLocal cleanup in finally block.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class InternalHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String userIdHeader = request.getHeader(SecurityHeaders.X_USER_ID);
        String tenantIdHeader = request.getHeader(SecurityHeaders.X_TENANT_ID);
        String rolesHeader = request.getHeader(SecurityHeaders.X_USER_ROLES);
        String permissionsHeader = request.getHeader(SecurityHeaders.X_USER_PERMISSIONS);
        String shopsHeader = request.getHeader(SecurityHeaders.X_USER_SHOPS);

        UUID tenantId = parseUuid(tenantIdHeader);
        UUID userId = parseUuid(userIdHeader);

        if (tenantId != null) {
            TenantContext.set(tenantId);
        }

        if (userId != null && tenantId != null) {
            List<GrantedAuthority> authorities = new ArrayList<>();

            // 1. Parse Roles (Ensure ROLE_ prefix for Spring Security hasRole() support)
            if (rolesHeader != null && !rolesHeader.isBlank()) {
                Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }

            // 2. Parse Permissions (e.g. order.create, product.view)
            if (permissionsHeader != null && !permissionsHeader.isBlank()) {
                Arrays.stream(permissionsHeader.split(","))
                        .map(String::trim)
                        .filter(p -> !p.isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }

            // 3. Parse Accessible Shop IDs
            Set<UUID> accessibleShopIds = new HashSet<>();
            if (shopsHeader != null && !shopsHeader.isBlank()) {
                Arrays.stream(shopsHeader.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(this::parseUuid)
                        .filter(s -> s != null)
                        .forEach(accessibleShopIds::add);
            }

            CommonUserDetails userDetails = new CommonUserDetails(userId, tenantId, accessibleShopIds, authorities);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
