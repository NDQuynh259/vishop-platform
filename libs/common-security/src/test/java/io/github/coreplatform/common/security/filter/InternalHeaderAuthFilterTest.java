package io.github.coreplatform.common.security.filter;

import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.context.CommonUserDetails;
import io.github.coreplatform.common.security.context.SecurityContextHelper;
import io.github.coreplatform.common.security.context.TenantContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InternalHeaderAuthFilterTest {

    private final InternalHeaderAuthFilter filter = new InternalHeaderAuthFilter();

    // #region tearDown
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }
    // #endregion

    // #region shouldAuthenticateWhenValidHeadersPresent
    @Test
    @DisplayName("Should authenticate user and set tenant context when valid headers are present")
    void shouldAuthenticateWhenValidHeadersPresent() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID shopId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityHeaders.X_USER_ID, userId.toString());
        request.addHeader(SecurityHeaders.X_TENANT_ID, tenantId.toString());
        request.addHeader(SecurityHeaders.X_USER_ROLES, "OWNER, OPERATOR");
        request.addHeader(SecurityHeaders.X_USER_PERMISSIONS, "order.create, product.view");
        request.addHeader(SecurityHeaders.X_USER_SHOPS, shopId.toString());

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainExecuted = new AtomicBoolean(false);

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                filterChainExecuted.set(true);

                // Verify inside execution context
                assertThat(TenantContext.get()).isEqualTo(tenantId);
                assertThat(SecurityContextHelper.getCurrentUser()).isPresent();

                CommonUserDetails user = SecurityContextHelper.requiredCurrentUser();
                assertThat(user.userId()).isEqualTo(userId);
                assertThat(user.tenantId()).isEqualTo(tenantId);
                assertThat(user.hasShopAccess(shopId)).isTrue();
                assertThat(user.hasAuthority("ROLE_OWNER")).isTrue();
                assertThat(user.hasAuthority("ROLE_OPERATOR")).isTrue();
                assertThat(user.hasAuthority("order.create")).isTrue();
                assertThat(user.hasAuthority("product.view")).isTrue();
                assertThat(user.hasAuthority("order.delete")).isFalse();
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(filterChainExecuted).isTrue();
        // Verify ThreadLocal cleanup in finally block
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.get()).isNull();
    }
    // #endregion

    // #region shouldPassThroughWhenNoHeaders
    @Test
    @DisplayName("Should pass through without authentication when headers are missing")
    void shouldPassThroughWhenNoHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainExecuted = new AtomicBoolean(false);

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                filterChainExecuted.set(true);
                assertThat(SecurityContextHelper.getCurrentUser()).isEmpty();
                assertThat(TenantContext.get()).isNull();
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(filterChainExecuted).isTrue();
    }
    // #endregion

    // #region shouldHandleInvalidUuidsGracefully
    @Test
    @DisplayName("Should handle invalid UUIDs gracefully without throwing exception")
    void shouldHandleInvalidUuidsGracefully() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityHeaders.X_USER_ID, "not-a-uuid");
        request.addHeader(SecurityHeaders.X_TENANT_ID, "not-a-uuid");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainExecuted = new AtomicBoolean(false);

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                filterChainExecuted.set(true);
                assertThat(SecurityContextHelper.getCurrentUser()).isEmpty();
                assertThat(TenantContext.get()).isNull();
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(filterChainExecuted).isTrue();
    }
    // #endregion
}
