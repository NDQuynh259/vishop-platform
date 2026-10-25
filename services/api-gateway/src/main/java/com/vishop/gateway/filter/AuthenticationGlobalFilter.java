package com.vishop.gateway.filter;

import com.vishop.gateway.security.AuthenticationFailure;
import com.vishop.gateway.security.LocalAuthenticationVerifier;
import io.github.coreplatform.common.security.constants.SecurityHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Stateless JWT verification; untrusted headers never confer access. */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {
  private final LocalAuthenticationVerifier authentication;
  private final Set<String> origins;

  public AuthenticationGlobalFilter(
      LocalAuthenticationVerifier authentication,
      @Value("${vishop.security.allowed-origins:http://localhost:3000}") String origins) {
    this.authentication = authentication;
    this.origins = Arrays.stream(origins.split(",")).map(String::trim).collect(Collectors.toSet());
  }

  @Override
  public Mono<Void> filter(ServerWebExchange original, GatewayFilterChain chain) {
    ServerHttpRequest request =
        original
            .getRequest()
            .mutate()
            .headers(
                h -> {
                  h.keySet().stream()
                      .filter(
                          key ->
                              key.toLowerCase(java.util.Locale.ROOT).startsWith("x-user-")
                                  || key.equalsIgnoreCase(SecurityHeaders.X_TENANT_ID)
                                  || key.equalsIgnoreCase("X-Iam-Internal-Key"))
                      .toList()
                      .forEach(h::remove);
                })
            .build();
    ServerWebExchange exchange = original.mutate().request(request).build();
    String path = request.getURI().getRawPath();
    String origin = request.getHeaders().getOrigin();
    if (origin != null && !origins.contains(origin)) return error(exchange, 403);
    if (request.getMethod() == HttpMethod.OPTIONS || publicPath(path))
      return chain.filter(exchange);

    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    String credential =
        authorization != null && authorization.startsWith("Bearer ")
            ? authorization.substring(7).trim()
            : null;
    if (credential == null || credential.isBlank()) return error(exchange, 401);

    return authentication
        .authenticate(credential, true)
        .materialize()
        .flatMap(
            signal -> {
              if (signal.isOnError()) {
                var failure = signal.getThrowable();
                return failure instanceof AuthenticationFailure rejected
                    ? error(exchange, rejected.status(), rejected.code())
                    : error(exchange, 503);
              }
              var identity = signal.get();
              if (identity == null) return error(exchange, 503);
              if (identity.tenantId() == null && !identityRoute(path)) return error(exchange, 403);
              var builder =
                  request
                      .mutate()
                      .headers(h -> h.remove(HttpHeaders.COOKIE))
                      .headers(
                          h -> {
                            h.setBearerAuth(identity.accessToken());
                            h.set(SecurityHeaders.X_USER_ID, identity.userId().toString());
                            if (identity.tenantId() != null)
                              h.set(SecurityHeaders.X_TENANT_ID, identity.tenantId().toString());
                          });
              return chain.filter(exchange.mutate().request(builder.build()).build());
            });
  }

  private boolean identityRoute(String path) {
    return path.startsWith("/api/v1/auth/")
        || path.equals("/api/v1/tenants")
        || path.startsWith("/api/v1/tenants/")
        || path.startsWith("/api/v1/cms/")
        || path.startsWith("/api/v1/invitations/");
  }

  private boolean publicPath(String path) {
    return Set.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/refresh",
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness")
            .contains(path)
        || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs");
  }

  private Mono<Void> error(ServerWebExchange exchange, int status) {
    return error(
        exchange,
        status,
        status == 401
            ? "unauthorized"
            : status == 403 ? "forbidden" : "authentication-unavailable");
  }

  private Mono<Void> error(ServerWebExchange exchange, int status, String code) {
    var response = exchange.getResponse();
    response.setStatusCode(HttpStatusCode.valueOf(status));
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String title =
        status == 401 ? "Unauthorized" : status == 403 ? "Forbidden" : "Authentication unavailable";
    byte[] json =
        ("{\"type\":\"/errors/"
                + code
                + "\",\"title\":\""
                + title
                + "\",\"status\":"
                + status
                + "}")
            .getBytes(StandardCharsets.UTF_8);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 2;
  }
}
