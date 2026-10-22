package com.vishop.gateway.filter;

import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global authentication and token mediation filter at the API Gateway.
 * Validates incoming JWT Bearer tokens or session cookies, enforces anti-spoofing
 * by stripping client-supplied X-User-* headers, and enriches downstream requests
 * with trusted internal security headers (X-User-Id, X-Tenant-Id, X-User-Roles, X-User-Permissions).
 */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationGlobalFilter.class);

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/v1/auth/",
            "/actuator/",
            "/swagger-ui",
            "/v3/api-docs"
    );

    private static final List<String> SESSION_COOKIE_NAMES = List.of(
            "better-auth.session_token",
            "vishop_session",
            "vishop_token"
    );

    private final JwtTokenProvider jwtTokenProvider;

    public AuthenticationGlobalFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // #region filter
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();

        // 1. Allow OPTIONS preflight and public endpoints
        if (request.getMethod() == HttpMethod.OPTIONS || isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // 2. Extract Token from Authorization Header or Cookie
        String token = extractToken(request);

        if (token == null || token.isBlank()) {
            log.warn("[Gateway Auth] Missing authorization token for protected route: {}", path);
            return unauthorizedResponse(exchange, "Full authentication is required to access this resource.");
        }

        // 3. Validate Token
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("[Gateway Auth] Invalid or expired token for route: {}", path);
            return unauthorizedResponse(exchange, "The provided authentication token is invalid or has expired.");
        }

        // 4. Extract Claims & Context Enrichment (Anti-Spoofing)
        UUID userId = jwtTokenProvider.extractUserId(token);
        UUID tenantId = jwtTokenProvider.extractTenantId(token);
        List<String> roles = jwtTokenProvider.extractRoles(token);
        List<String> permissions = jwtTokenProvider.extractPermissions(token);

        ServerHttpRequest.Builder requestBuilder = request.mutate()
                // Strip client-supplied security headers to prevent spoofing
                .headers(headers -> {
                    headers.remove(SecurityHeaders.X_USER_ID);
                    headers.remove(SecurityHeaders.X_USER_ROLES);
                    headers.remove(SecurityHeaders.X_USER_PERMISSIONS);
                    headers.remove(SecurityHeaders.X_USER_SHOPS);
                })
                .header(SecurityHeaders.X_USER_ID, userId.toString())
                .header(SecurityHeaders.X_USER_ROLES, String.join(",", roles))
                .header(SecurityHeaders.X_USER_PERMISSIONS, String.join(",", permissions));

        if (tenantId != null) {
            requestBuilder.header(SecurityHeaders.X_TENANT_ID, tenantId.toString());
        }

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }
    // #endregion

    // #region extractToken
    private String extractToken(ServerHttpRequest request) {
        // A. Check Authorization: Bearer <token>
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // B. Check Session Cookies (Hybrid Mode)
        for (String cookieName : SESSION_COOKIE_NAMES) {
            HttpCookie cookie = request.getCookies().getFirst(cookieName);
            if (cookie != null && !cookie.getValue().isBlank()) {
                return cookie.getValue().trim();
            }
        }

        return null;
    }
    // #endregion

    // #region isPublicPath
    private boolean isPublicPath(String path) {
        if (path == null) return false;
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    // #endregion

    // #region unauthorizedResponse
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        String requestId = exchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = "none";
        }

        String json = String.format("""
                {
                  "type": "/errors/unauthorized",
                  "title": "Unauthorized",
                  "status": 401,
                  "detail": "%s",
                  "code": "unauthorized",
                  "timestamp": "%s",
                  "requestId": "%s"
                }
                """,
                detail,
                Instant.now().toString(),
                requestId
        );

        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
    // #endregion

    // #region getOrder
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
    // #endregion
}
