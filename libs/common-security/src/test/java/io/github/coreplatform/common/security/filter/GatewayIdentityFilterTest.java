package io.github.coreplatform.common.security.filter;

import static io.github.coreplatform.common.security.constants.SecurityHeaders.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.security.context.TenantContext;
import io.github.coreplatform.common.security.gateway.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class GatewayIdentityFilterTest {
  final UUID user = UUID.randomUUID(),
      sid = UUID.randomUUID(),
      tenant = UUID.randomUUID(),
      role = UUID.randomUUID();
  final GatewayRolePermissions roles = mock(GatewayRolePermissions.class);
  final GatewayIdentityFilter filter = new GatewayIdentityFilter(roles);

  MockHttpServletRequest request() {
    var request = new MockHttpServletRequest("GET", "/api/v1/example");
    new GatewayIdentityHeaders(user, sid, tenant, role, "USER")
        .toHeaders()
        .forEach((name, values) -> values.forEach(value -> request.addHeader(name, value)));
    return request;
  }

  @Test
  void resolvesActualPermissionsAndClearsThreadState() throws Exception {
    when(roles.resolve(tenant, role))
        .thenReturn(new GatewayRolePermissions.Grant("CUSTOM:" + role, Set.of("inventory.view")));
    var request = request();
    request.addHeader(X_PERMISSIONS, "system.admin");
    request.addHeader(X_USER_PERMISSIONS, "system.admin");
    request.addHeader(X_TENANT_ROLE, "OWNER");
    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          var actor =
              (GatewayPrincipal)
                  SecurityContextHolder.getContext().getAuthentication().getPrincipal();
          assertThat(actor.userId()).isEqualTo(user);
          assertThat(actor.sessionId()).isEqualTo(sid);
          assertThat(actor.permissions()).containsExactly("inventory.view");
          assertThat(actor.systemRole()).isEqualTo("USER");
          assertThat(actor.tenantRole()).isEqualTo("CUSTOM:" + role);
          assertThat(TenantContext.get()).isEqualTo(tenant);
        });
    verify(roles).resolve(tenant, role);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(TenantContext.get()).isNull();
  }

  @Test
  void rejectsMissingMalformedOrDuplicateIdentityFieldsBeforeRedis() throws Exception {
    for (String field : Set.of(X_USER_ID, X_SESSION_ID, X_TENANT_ID, X_ROLE_ID, X_SYSTEM_ROLE)) {
      var request = request();
      request.removeHeader(field);
      var response = new MockHttpServletResponse();
      filter.doFilter(
          request, response, (req, res) -> fail("Must not forward incomplete identity"));
      assertThat(response.getStatus()).isEqualTo(401);
    }
    var request = request();
    request.addHeader(X_SESSION_ID, UUID.randomUUID().toString());
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> fail("Must reject duplicate identity"));
    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoInteractions(roles);
  }

  @Test
  void redisFailureAndInactiveRoleFailClosed() throws Exception {
    when(roles.resolve(tenant, role)).thenThrow(new IllegalStateException("Redis down"));
    var response = new MockHttpServletResponse();
    filter.doFilter(
        request(), response, (req, res) -> fail("Must not allow missing authorization"));
    assertThat(response.getStatus()).isEqualTo(503);
    doThrow(new ForbiddenException("Role inactive")).when(roles).resolve(tenant, role);
    response = new MockHttpServletResponse();
    filter.doFilter(request(), response, (req, res) -> fail("Must not allow inactive role"));
    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void tenantlessIdentityDoesNotReadRedisAndTechnicalIdentityCannotAccessBusinessApis()
      throws Exception {
    var request = new MockHttpServletRequest();
    new GatewayIdentityHeaders(user, sid, null, null, "PLATFORM_ADMIN")
        .toHeaders()
        .forEach((name, values) -> values.forEach(value -> request.addHeader(name, value)));
    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          var actor =
              (GatewayPrincipal)
                  SecurityContextHolder.getContext().getAuthentication().getPrincipal();
          assertThat(actor.permissions()).isEmpty();
        });
    var technical = new MockHttpServletRequest();
    GatewayIdentityHeaders.technical()
        .toHeaders()
        .forEach((name, values) -> values.forEach(value -> technical.addHeader(name, value)));
    var response = new MockHttpServletResponse();
    filter.doFilter(
        technical, response, (req, res) -> fail("Technical identity cannot become user"));
    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoInteractions(roles);
  }

  @Test
  void downstreamExceptionsAreNotReportedAsAuthenticationFailures() {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (req, res) -> {
                      throw new IllegalStateException("business failure");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("business failure");
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
