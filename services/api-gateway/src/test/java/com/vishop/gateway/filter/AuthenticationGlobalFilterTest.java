package com.vishop.gateway.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.vishop.gateway.handler.AuthenticationErrorResponse;
import com.vishop.gateway.security.GatewayRequestForwarder;
import com.vishop.gateway.security.GatewayRequestPolicy;
import com.vishop.gateway.security.LocalAuthenticationVerifier;
import com.vishop.gateway.security.LocalAuthenticationVerifier.Identity;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.gateway.GatewayIdentityHeaders;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticationGlobalFilterTest {
  private final UUID user = UUID.randomUUID(),
      tenant = UUID.randomUUID(),
      session = UUID.randomUUID();
  private LocalAuthenticationVerifier authentication;
  private GatewayFilterChain chain;
  private AuthenticationGlobalFilter filter;
  private Identity identity;

  @BeforeEach
  void setup() {
    authentication = mock(LocalAuthenticationVerifier.class);
    chain = mock(GatewayFilterChain.class);
    var policy = new GatewayRequestPolicy("http://localhost:3000");
    filter =
        new AuthenticationGlobalFilter(
            authentication,
            policy,
            new GatewayRequestForwarder(policy),
            new AuthenticationErrorResponse());
    identity =
        new Identity(
            user,
            tenant,
            UUID.randomUUID(),
            new io.github.coreplatform.common.security.gateway.GatewayPrincipal(
                user, tenant, "USER", "OWNER", Set.of("tenant.view"), session));
    when(chain.filter(any())).thenReturn(Mono.empty());
    when(authentication.authenticate(anyString(), anyBoolean()))
        .thenReturn(Mono.error(new UnauthorizedException("unauthorized", "unauthorized")));
    when(authentication.authenticate("valid-token", true)).thenReturn(Mono.just(identity));
  }

  private MockServerWebExchange run(MockServerHttpRequest request) {
    var exchange = MockServerWebExchange.from(request);
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    return exchange;
  }

  @ParameterizedTest
  @CsvSource({"401, token-revoked", "403, access-not-active"})
  void commonExceptionsPreserveAuthenticationStatusAndCode(int status, String code) {
    RuntimeException failure =
        status == 401
            ? new UnauthorizedException(code, "Credential rejected")
            : new ForbiddenException(code, "Access denied");
    when(authentication.authenticate("rejected-token", true)).thenReturn(Mono.error(failure));

    var exchange =
        run(
            MockServerHttpRequest.get("/api/v1/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer rejected-token")
                .build());

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.valueOf(status));
    assertThat(exchange.getResponse().getBodyAsString().block())
        .contains("\"type\":\"/errors/" + code + "\"");
    verify(chain, never()).filter(any());
  }

  private ServerWebExchange forwarded() {
    var captor = ArgumentCaptor.forClass(ServerWebExchange.class);
    verify(chain).filter(captor.capture());
    return captor.getValue();
  }

  @Test
  void sepayPostCallbackReachesProviderAuthenticationWithoutBrowserCredentials() {
    run(
        MockServerHttpRequest.post("/api/v1/billing/webhooks/sepay")
            .header("X-SePay-Timestamp", "1234567890")
            .header("X-SePay-Signature", "provider-signature")
            .header("X-User-Id", user.toString())
            .body("{\"id\":1}"));
    assertThat(forwarded().getRequest().getHeaders().getFirst("X-SePay-Signature"))
        .isEqualTo("provider-signature");
    assertThat(forwarded().getRequest().getHeaders()).doesNotContainKey("X-User-Id");
    verifyNoInteractions(authentication);
  }

  @Test
  void sepayExceptionDoesNotOpenOtherBillingPathsOrHttpMethods() {
    var exchange = run(MockServerHttpRequest.get("/api/v1/billing/webhooks/sepay").build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(chain, never()).filter(any());
    var checkout = run(MockServerHttpRequest.post("/api/v1/subscriptions/orders").build());
    assertThat(checkout.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verify(chain, never()).filter(any());
  }

  @Test
  void publicRoutesStripSpoofedIdentityAndInternalSecret() {
    run(
        MockServerHttpRequest.post("/api/v1/auth/login")
            .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
            .header("X-User-Id", user.toString())
            .header("X-Tenant-Id", tenant.toString())
            .header("X-Iam-Internal-Key", "spoofed")
            .build());
    assertThat(forwarded().getRequest().getHeaders())
        .doesNotContainKeys("X-User-Id", "X-Tenant-Id", "X-Iam-Internal-Key");
    verifyNoInteractions(authentication);
  }

  @Test
  void optionsDoesNotRequireCredentials() {
    run(MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/tenants").build());
    verify(chain).filter(any());
    verifyNoInteractions(authentication);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "x-user-id",
        "X-TENANT-ID",
        "X-User-Roles",
        "x-USER-permissions",
        "X-User-Shops",
        "x-gateway-context",
        "X-IAM-INTERNAL-KEY",
        "X-User-Email",
        "X-Gateway-Custom"
      })
  void internalHeadersAreRemovedCaseInsensitivelyWithoutRemovingOrdinaryHeaders(String header) {
    run(
        MockServerHttpRequest.post("/api/v1/auth/login")
            .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
            .header(header, "spoofed")
            .header("X-Correlation-Id", "keep-this")
            .build());
    var headers = forwarded().getRequest().getHeaders();
    assertThat(headers).doesNotContainKey(header);
    assertThat(headers.getFirst("X-Correlation-Id")).isEqualTo("keep-this");
    verifyNoInteractions(authentication);
  }

  @Test
  void identityEndpointsStillRequireAuthentication() {
    var exchange = run(MockServerHttpRequest.get("/api/v1/auth/me").build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(authentication);
    verify(chain, never()).filter(any());
  }

  @Test
  void rejectedAndRevokedCredentialsNeverReachDownstream() {
    var exchange =
        run(
            MockServerHttpRequest.get("/api/v1/tenants")
                .header("Authorization", "Bearer revoked")
                .build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(chain, never()).filter(any());
  }

  @Test
  void validBearerIsReplacedByIdentityHeadersWithoutPermissions() {
    run(
        MockServerHttpRequest.get("/api/v1/tenants")
            .header("Authorization", "Bearer valid-token")
            .header(SecurityHeaders.X_USER_ID, "spoofed")
            .header(SecurityHeaders.X_TENANT_ID, UUID.randomUUID().toString())
            .header(SecurityHeaders.X_USER_ROLES, SystemRole.PLATFORM_ADMIN.name())
            .header(SecurityHeaders.X_USER_PERMISSIONS, "system.admin")
            .header(SecurityHeaders.X_SESSION_ID, "spoofed-session")
            .header(SecurityHeaders.X_SYSTEM_ROLE, "PLATFORM_ADMIN")
            .header(SecurityHeaders.X_ROLE_ID, UUID.randomUUID().toString())
            .header(SecurityHeaders.X_TENANT_ROLE, "forged-role")
            .header(SecurityHeaders.X_PERMISSIONS, "system.admin")
            .header(SecurityHeaders.X_GATEWAY_CONTEXT, "spoofed-context")
            .build());
    var headers = forwarded().getRequest().getHeaders();
    assertThat(headers.getFirst("Authorization")).isNull();
    assertThat(headers.getFirst(SecurityHeaders.X_USER_ID)).isEqualTo(user.toString());
    assertThat(headers.getFirst(SecurityHeaders.X_TENANT_ID)).isEqualTo(tenant.toString());
    assertThat(headers.getFirst("X-Gateway-Context")).isNull();
    var context = GatewayIdentityHeaders.read(headers::get);
    assertThat(context.getUserId()).isEqualTo(user);
    assertThat(context.getTenantId()).isEqualTo(tenant);
    assertThat(context.getSystemRole()).isEqualTo("USER");
    assertThat(context.getRoleId()).isEqualTo(identity.roleId());
    assertThat(headers)
        .doesNotContainKeys(SecurityHeaders.X_PERMISSIONS, SecurityHeaders.X_USER_PERMISSIONS);
    assertThat(context.getSessionId()).isEqualTo(session);
    assertThat(headers.keySet().stream().filter(SecurityHeaders::isInternalHeader))
        .containsExactlyInAnyOrder(
            SecurityHeaders.X_USER_ID,
            SecurityHeaders.X_SESSION_ID,
            SecurityHeaders.X_TENANT_ID,
            SecurityHeaders.X_ROLE_ID,
            SecurityHeaders.X_SYSTEM_ROLE);
    // Permissions and obsolete identity headers never cross the gateway.
    assertThat(headers)
        .doesNotContainKeys(SecurityHeaders.X_USER_ROLES, SecurityHeaders.X_USER_PERMISSIONS);
  }

  @Test
  void unrelatedCookiesNeverAuthenticateBusinessRequests() {
    var exchange =
        run(
            MockServerHttpRequest.get("/api/v1/tenants")
                .cookie(new HttpCookie("vishop_session", "opaque-cookie"))
                .build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(authentication);
    verify(chain, never()).filter(any());
  }

  @Test
  void refreshReachesIamEvenWithExpiredAccessToken() {
    run(
        MockServerHttpRequest.post("/api/v1/auth/refresh")
            .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
            .header("Authorization", "Bearer expired")
            .build());
    verify(chain).filter(any());
    verifyNoInteractions(authentication);
  }

  @Test
  void tenantlessIdentityCanUseIamButCannotUseBusinessApis() {
    var global =
        new Identity(
            user,
            null,
            null,
            new io.github.coreplatform.common.security.gateway.GatewayPrincipal(
                user, null, "USER", null, Set.of(), session));
    when(authentication.authenticate("identity", true)).thenReturn(Mono.just(global));
    run(
        MockServerHttpRequest.get("/api/v1/auth/me")
            .header("Authorization", "Bearer identity")
            .build());
    verify(chain).filter(any());
    clearInvocations(chain);
    var exchange =
        run(
            MockServerHttpRequest.get("/api/v1/orders")
                .header("Authorization", "Bearer identity")
                .build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verify(chain, never()).filter(any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/cms",
        "/api/v1/cms/audit-logs",
        "/api/v1/%63ms/audit-logs",
        "/api/v1/cms;ignored=x/audit-logs"
      })
  void cmsRejectsTenantOwnerEvenWithSpoofedSystemAdminHeader(String path) {
    var exchange =
        run(
            MockServerHttpRequest.method(HttpMethod.GET, java.net.URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header(SecurityHeaders.X_SYSTEM_ROLE, "PLATFORM_ADMIN")
                .build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verify(chain, never()).filter(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void cmsAllowsSystemAdminAndForwardsSelectedTenantWhenPresent(boolean selectedTenant) {
    UUID tenantId = selectedTenant ? tenant : null;
    UUID roleId = selectedTenant ? UUID.randomUUID() : null;
    var admin =
        new Identity(
            user,
            tenantId,
            roleId,
            new io.github.coreplatform.common.security.gateway.GatewayPrincipal(
                user,
                tenantId,
                SystemRole.PLATFORM_ADMIN.name(),
                selectedTenant ? "MEMBER" : null,
                Set.of(),
                session));
    when(authentication.authenticate("admin-token", true)).thenReturn(Mono.just(admin));
    run(
        MockServerHttpRequest.get("/api/v1/cms/audit-logs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
            .build());
    var headers = forwarded().getRequest().getHeaders();
    var forwardedIdentity = GatewayIdentityHeaders.read(headers::get);
    assertThat(forwardedIdentity.getSystemRole()).isEqualTo(SystemRole.PLATFORM_ADMIN.name());
    assertThat(forwardedIdentity.getTenantId()).isEqualTo(tenantId);
    assertThat(forwardedIdentity.getRoleId()).isEqualTo(roleId);
  }

  @Test
  void businessRequestForwardsAuthenticatedTenantInsteadOfClientTenant() {
    run(
        MockServerHttpRequest.get("/api/v1/notification-settings/channels")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header(SecurityHeaders.X_TENANT_ID, UUID.randomUUID().toString())
            .header(SecurityHeaders.X_ROLE_ID, UUID.randomUUID().toString())
            .build());
    var headers = forwarded().getRequest().getHeaders();
    var forwardedIdentity = GatewayIdentityHeaders.read(headers::get);
    assertThat(forwardedIdentity.getTenantId()).isEqualTo(tenant);
    assertThat(forwardedIdentity.getRoleId()).isEqualTo(identity.roleId());
  }

  @Test
  void notificationSettingsRequireTenantBeforeForwarding() {
    var tenantless =
        new Identity(
            user,
            null,
            null,
            new io.github.coreplatform.common.security.gateway.GatewayPrincipal(
                user, null, "USER", null, Set.of(), session));
    when(authentication.authenticate("tenantless", true)).thenReturn(Mono.just(tenantless));
    var exchange =
        run(
            MockServerHttpRequest.get("/api/v1/notification-settings/channels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer tenantless")
                .build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verify(chain, never()).filter(any());
  }

  @Test
  void unexpectedVerificationFailureFailsClosed() {
    when(authentication.authenticate("valid-token", true))
        .thenReturn(Mono.error(new IllegalStateException("IAM unavailable")));
    var exchange =
        run(
            MockServerHttpRequest.get("/api/v1/tenants")
                .header("Authorization", "Bearer valid-token")
                .build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    verify(chain, never()).filter(any());
  }

  @Test
  void malformedAuthorizationDoesNotFallBackToCookie() {
    var exchange =
        run(
            MockServerHttpRequest.get("/api/v1/tenants")
                .header("Authorization", "Basic bad")
                .cookie(new HttpCookie(AuthCookies.ACCESS_TOKEN, "valid-token"))
                .build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(authentication);
  }

  @Test
  void accessCookieAuthenticatesWithoutForwardingBrowserCookiesToBusinessServices() {
    run(
        MockServerHttpRequest.get("/api/v1/audit/probe")
            .cookie(
                new HttpCookie(AuthCookies.ACCESS_TOKEN, "valid-token"),
                new HttpCookie(AuthCookies.REFRESH_TOKEN, "private-refresh"),
                new HttpCookie("other", "private"))
            .build());
    verify(authentication).authenticate("valid-token", true);
    assertThat(forwarded().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    assertThat(forwarded().getRequest().getHeaders().getFirst(HttpHeaders.COOKIE)).isNull();
  }

  @Test
  void logoutForwardsOnlyRefreshCookieAndVerifiedAccessContextToIam() {
    run(
        MockServerHttpRequest.post("/api/v1/auth/logout")
            .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
            .cookie(
                new HttpCookie(AuthCookies.ACCESS_TOKEN, "valid-token"),
                new HttpCookie(AuthCookies.REFRESH_TOKEN, "private-refresh"),
                new HttpCookie("other", "private"))
            .build());
    assertThat(forwarded().getRequest().getHeaders().getFirst(HttpHeaders.COOKIE))
        .isEqualTo(AuthCookies.REFRESH_TOKEN + "=private-refresh");
    var headers = forwarded().getRequest().getHeaders();
    assertThat(headers).doesNotContainKey(HttpHeaders.AUTHORIZATION);
    var context = GatewayIdentityHeaders.read(headers::get);
    assertThat(context.getUserId()).isEqualTo(user);
    assertThat(context.getSessionId()).isEqualTo(session);
    verify(authentication).authenticate("valid-token", true);
  }

  @Test
  void refreshForwardsCookieWithoutVerifyingExpiredAccessCookie() {
    run(
        MockServerHttpRequest.post("/api/v1/auth/refresh")
            .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
            .cookie(
                new HttpCookie(AuthCookies.ACCESS_TOKEN, "expired"),
                new HttpCookie(AuthCookies.REFRESH_TOKEN, "private-refresh"))
            .build());
    verifyNoInteractions(authentication);
    assertThat(forwarded().getRequest().getHeaders().getFirst(HttpHeaders.COOKIE))
        .isEqualTo(AuthCookies.REFRESH_TOKEN + "=private-refresh");
    assertThat(forwarded().getRequest().getHeaders())
        .doesNotContainKeys(HttpHeaders.AUTHORIZATION, SecurityHeaders.X_GATEWAY_CONTEXT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"expired", "revoked", ""})
  void logoutStillReachesIamWithRefreshWhenAccessCannotAuthenticate(String access) {
    run(
        MockServerHttpRequest.post("/api/v1/auth/logout")
            .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
            .header(SecurityHeaders.X_GATEWAY_CONTEXT, "spoofed")
            .cookie(
                new HttpCookie(AuthCookies.ACCESS_TOKEN, access),
                new HttpCookie(AuthCookies.REFRESH_TOKEN, "private-refresh"))
            .build());
    var headers = forwarded().getRequest().getHeaders();
    assertThat(headers.getFirst(HttpHeaders.COOKIE))
        .isEqualTo(AuthCookies.REFRESH_TOKEN + "=private-refresh");
    assertThat(headers)
        .doesNotContainKeys(HttpHeaders.AUTHORIZATION, SecurityHeaders.X_GATEWAY_CONTEXT);
  }

  @ParameterizedTest
  @CsvSource({
    "GET,/api/v1/auth/refresh",
    "OPTIONS,/api/v1/auth/refresh",
    "GET,/api/v1/auth/logout",
    "POST,/api/v1/auth/switch-tenant",
    "POST,/api/v1/auth/login",
    "POST,/api/v1/auth/register",
    "GET,/api/v1/tenants"
  })
  void otherEndpointsAndMethodsNeverReceiveRawTokens(String method, String path) {
    run(
        MockServerHttpRequest.method(HttpMethod.valueOf(method), path)
            .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .cookie(
                new HttpCookie(AuthCookies.ACCESS_TOKEN, "valid-token"),
                new HttpCookie(AuthCookies.REFRESH_TOKEN, "private-refresh"))
            .build());
    assertThat(forwarded().getRequest().getHeaders())
        .doesNotContainKeys(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE);
  }

  @Test
  void refreshCookieNeverReachesAnotherServiceEvenWhenAuthPathMatches() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/refresh")
                .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
                .cookie(new HttpCookie(AuthCookies.REFRESH_TOKEN, "private-refresh")));
    var route = mock(org.springframework.cloud.gateway.route.Route.class);
    when(route.getId()).thenReturn("subscription-service");
    exchange
        .getAttributes()
        .put(
            org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
            route);
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat(forwarded().getRequest().getHeaders()).doesNotContainKey(HttpHeaders.COOKIE);
  }

  @Test
  void ambiguousRefreshCookiesAreNotForwarded() {
    run(
        MockServerHttpRequest.post("/api/v1/auth/refresh")
            .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
            .cookie(
                new HttpCookie(AuthCookies.REFRESH_TOKEN, "first"),
                new HttpCookie(AuthCookies.REFRESH_TOKEN, "second"))
            .build());
    assertThat(forwarded().getRequest().getHeaders()).doesNotContainKey(HttpHeaders.COOKIE);
  }

  @Test
  void cookieWritesWithoutCsrfHeaderAreRejectedBeforeForwarding() {
    var exchange = run(MockServerHttpRequest.post("/api/v1/auth/login").build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verify(chain, never()).filter(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/tenants", "/api/v1/auth/logout"})
  void downstreamFailureIsPropagatedWithoutRetryingOrBecomingAuthenticationError(String path) {
    var failure = new IllegalStateException("Downstream failed");
    when(chain.filter(any())).thenReturn(Mono.error(failure));
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post(path)
                .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"));

    StepVerifier.create(filter.filter(exchange, chain))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();

    verify(chain, times(1)).filter(any());
    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }
}
