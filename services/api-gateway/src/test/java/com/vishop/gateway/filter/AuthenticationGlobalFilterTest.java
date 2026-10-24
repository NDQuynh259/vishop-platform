package com.vishop.gateway.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.vishop.gateway.security.IamAuthenticationClient;
import com.vishop.gateway.security.IamAuthenticationClient.Identity;
import io.github.coreplatform.common.security.constants.SecurityHeaders;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticationGlobalFilterTest {
  private final UUID user = UUID.randomUUID(), tenant = UUID.randomUUID();
  private IamAuthenticationClient iam;
  private GatewayFilterChain chain;
  private AuthenticationGlobalFilter filter;
  private Identity identity;

  @BeforeEach
  void setup() {
    iam = mock(IamAuthenticationClient.class);
    chain = mock(GatewayFilterChain.class);
    filter = new AuthenticationGlobalFilter(iam, "http://localhost:3000");
    identity =
        new Identity(
            user,
            tenant,
            "USER",
            List.of("OWNER"),
            Set.of("tenant.view", "order.create"),
            "trusted-jwt");
    when(chain.filter(any())).thenReturn(Mono.empty());
    when(iam.authenticate(anyString(), anyBoolean()))
        .thenReturn(
            Mono.error(
                WebClientResponseException.create(
                    401, "Unauthorized", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8)));
    when(iam.authenticate("valid-token", true)).thenReturn(Mono.just(identity));
    when(iam.authenticate("opaque-cookie", false)).thenReturn(Mono.just(identity));
  }

  private MockServerWebExchange run(MockServerHttpRequest request) {
    var exchange = MockServerWebExchange.from(request);
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    return exchange;
  }

  private ServerWebExchange forwarded() {
    var captor = ArgumentCaptor.forClass(ServerWebExchange.class);
    verify(chain).filter(captor.capture());
    return captor.getValue();
  }

  @Test
  void publicRoutesStripSpoofedIdentityAndInternalSecret() {
    run(
        MockServerHttpRequest.post("/api/v1/auth/login")
            .header("X-User-Id", user.toString())
            .header("X-Tenant-Id", tenant.toString())
            .header("X-Iam-Internal-Key", "spoofed")
            .build());
    assertThat(forwarded().getRequest().getHeaders())
        .doesNotContainKeys("X-User-Id", "X-Tenant-Id", "X-Iam-Internal-Key");
    verifyNoInteractions(iam);
  }

  @Test
  void optionsDoesNotRequireCredentials() {
    run(MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/tenants").build());
    verify(chain).filter(any());
    verifyNoInteractions(iam);
  }

  @Test
  void identityEndpointsStillRequireAuthentication() {
    var exchange = run(MockServerHttpRequest.get("/api/v1/auth/me").build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(iam);
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
  void validBearerUsesLiveIamGrantsAndReplacesUntrustedContext() {
    run(
        MockServerHttpRequest.get("/api/v1/tenants")
            .header("Authorization", "Bearer valid-token")
            .header(SecurityHeaders.X_USER_ID, "spoofed")
            .header(SecurityHeaders.X_TENANT_ID, UUID.randomUUID().toString())
            .header(SecurityHeaders.X_USER_ROLES, "PLATFORM_ADMIN")
            .header(SecurityHeaders.X_USER_PERMISSIONS, "system.admin")
            .build());
    var headers = forwarded().getRequest().getHeaders();
    assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer trusted-jwt");
    assertThat(headers.getFirst(SecurityHeaders.X_USER_ID)).isEqualTo(user.toString());
    assertThat(headers.getFirst(SecurityHeaders.X_TENANT_ID)).isEqualTo(tenant.toString());
    // Roles/grants stay inside the signed JWT; arbitrary role names never become HTTP headers.
    assertThat(headers)
        .doesNotContainKeys(SecurityHeaders.X_USER_ROLES, SecurityHeaders.X_USER_PERMISSIONS);
  }

  @Test
  void opaqueCookieIsMediatedIntoJwtWithoutForwardingCookie() {
    run(
        MockServerHttpRequest.get("/api/v1/tenants")
            .cookie(new HttpCookie("vishop_session", "opaque-cookie"))
            .build());
    assertThat(forwarded().getRequest().getHeaders().getFirst("Authorization"))
        .isEqualTo("Bearer trusted-jwt");
    verify(iam).authenticate("opaque-cookie", false);
  }

  @Test
  void cookieWritesRequireAnAllowedOriginBeforeMediation() {
    var exchange =
        run(
            MockServerHttpRequest.post("/api/v1/auth/logout")
                .cookie(new HttpCookie("vishop_session", "opaque-cookie"))
                .build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(iam);
    verify(chain, never()).filter(any());
  }

  @Test
  void cookieWritesWithAllowedOriginProceed() {
    run(
        MockServerHttpRequest.post("/api/v1/auth/logout")
            .cookie(new HttpCookie("vishop_session", "opaque-cookie"))
            .header("Origin", "http://localhost:3000")
            .build());
    verify(chain).filter(any());
    verify(iam).authenticate("opaque-cookie", false);
  }

  @Test
  void tenantlessIdentityCanUseIamButCannotUseBusinessApis() {
    var global = new Identity(user, null, "USER", List.of(), Set.of(), "identity-jwt");
    when(iam.authenticate("identity", true)).thenReturn(Mono.just(global));
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

  @Test
  void iamFailureFailsClosed() {
    when(iam.authenticate("valid-token", true))
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
                .cookie(new HttpCookie("vishop_session", "opaque-cookie"))
                .build());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(iam);
  }
}
