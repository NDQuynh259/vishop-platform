package com.vishop.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticationGlobalFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private AuthenticationGlobalFilter filter;
    private GatewayFilterChain chain;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private String validToken;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "vishop-platform-super-secret-key-that-is-at-least-256-bits-long",
                3600000L);
        filter = new AuthenticationGlobalFilter(jwtTokenProvider);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        validToken = jwtTokenProvider.generateAccessToken(
                userId, tenantId, "user@vishop.vn", "Nguyen Van A",
                List.of("OWNER"), List.of("tenant.view", "order.create"));
    }

    @Test
    @DisplayName("Should allow public authentication paths without token")
    void shouldAllowPublicPath() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Should allow OPTIONS preflight requests without token")
    void shouldAllowOptionsMethod() {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/tenants").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Should reject protected route with 401 when token is missing")
    void shouldRejectWhenTokenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tenants").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("Should reject protected route with 401 when token is invalid")
    void shouldRejectWhenTokenInvalid() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tenants")
                .header("Authorization", "Bearer invalid-garbage-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should authenticate valid Bearer token, strip spoofed headers, and enrich context")
    void shouldAuthenticateValidBearerToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tenants")
                .header("Authorization", "Bearer " + validToken)
                // Spoofed header from malicious client
                .header(SecurityHeaders.X_USER_ID, "malicious-user-id")
                .header(SecurityHeaders.X_USER_ROLES, "SUPER_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange captured = captor.getValue();
        // Verify genuine claims injected
        assertThat(captured.getRequest().getHeaders().getFirst(SecurityHeaders.X_USER_ID))
                .isEqualTo(userId.toString());
        assertThat(captured.getRequest().getHeaders().getFirst(SecurityHeaders.X_TENANT_ID))
                .isEqualTo(tenantId.toString());
        assertThat(captured.getRequest().getHeaders().getFirst(SecurityHeaders.X_USER_ROLES))
                .isEqualTo("OWNER");
        assertThat(captured.getRequest().getHeaders().getFirst(SecurityHeaders.X_USER_PERMISSIONS))
                .contains("tenant.view", "order.create");
    }

    @Test
    @DisplayName("Should authenticate valid session cookie in hybrid mode")
    void shouldAuthenticateValidCookie() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tenants")
                .cookie(new HttpCookie("better-auth.session_token", validToken))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange captured = captor.getValue();
        assertThat(captured.getRequest().getHeaders().getFirst(SecurityHeaders.X_USER_ID))
                .isEqualTo(userId.toString());
    }
}
