package com.vishop.gateway.filter;

import com.vishop.gateway.security.AuthenticationFailure;
import com.vishop.gateway.security.LocalAuthenticationVerifier;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.security.constants.AuthCookies;
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

/** Authenticates at ingress and propagates request-bound, service-specific trusted context. */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {
  private final LocalAuthenticationVerifier authentication;
  private final com.vishop.gateway.security.GatewayContextSigners signers;

  private final Set<String> origins;

  /**
   * Nhận danh sách Origin phân cách bằng dấu phẩy từ cấu hình. Nếu chưa cấu hình, mặc định cho phép
   * http://localhost:3000.
   */
  public AuthenticationGlobalFilter(
      LocalAuthenticationVerifier authentication,
      com.vishop.gateway.security.GatewayContextSigners signers,
      @Value("${vishop.security.allowed-origins:http://localhost:3000,http://localhost:8000}")
          String origins) {
    this.authentication = authentication;
    this.signers = signers;
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
                                  || key.equalsIgnoreCase("X-Iam-Internal-Key")
                                  || key.toLowerCase(java.util.Locale.ROOT)
                                      .startsWith("x-gateway-"))
                      .toList()
                      .forEach(h::remove);
                })
            .build();
    ServerWebExchange exchange = original.mutate().request(request).build();
    String path = request.getURI().getRawPath();
    boolean sepayWebhook =
        request.getMethod() == HttpMethod.POST
            && path.equals(ApiPaths.V1 + "/billing/webhooks/sepay");
    String origin = request.getHeaders().getOrigin();
    if (origin != null && !origins.contains(origin)) return error(exchange, 403);
    if (!sepayWebhook
        && AuthCookies.requiresRequestHeader(request.getMethod().name())
        && !AuthCookies.REQUEST_HEADER_VALUE.equals(
            request.getHeaders().getFirst(AuthCookies.REQUEST_HEADER)))
      return error(exchange, 403, "csrf-request-header-required");
    if (request.getMethod() == HttpMethod.OPTIONS || sepayWebhook || publicPath(path))
      return chain.filter(exchange.mutate().request(forwardCookies(request, path).build()).build());

    boolean logout = path.equals(ApiPaths.V1 + "/auth/logout");
    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    String credential =
        authorization != null
            ? authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null
            : accessCookie(request);
    if (credential == null || credential.isBlank())
      return logout
          ? chain.filter(exchange.mutate().request(forwardCookies(request, path).build()).build())
          : error(exchange, 401);

    return authentication
        .authenticate(credential, true)
        .materialize()
        .flatMap(
            signal -> {
              if (signal.isOnError()) {
                if (logout)
                  return chain.filter(
                      exchange.mutate().request(forwardCookies(request, path).build()).build());
                var failure = signal.getThrowable();
                return failure instanceof AuthenticationFailure rejected
                    ? error(exchange, rejected.status(), rejected.code())
                    : error(exchange, 503);
              }
              var identity = signal.get();
              if (identity == null) return error(exchange, 503);
              if (identity.tenantId() == null && !identityRoute(path)) return error(exchange, 403);
              var builder =
                  forwardCookies(request, path)
                      .headers(
                          h -> {
                            h.remove(HttpHeaders.AUTHORIZATION);
                            var route =
                                exchange
                                    .<org.springframework.cloud.gateway.route.Route>getAttribute(
                                        org.springframework.cloud.gateway.support
                                            .ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                            String audience =
                                route == null
                                    ? "iam-service"
                                    : route.getId().equals("cms-audit-service")
                                        ? "audit-service"
                                        : route.getId();
                            String target =
                                request.getURI().getRawPath()
                                    + (request.getURI().getRawQuery() == null
                                        ? ""
                                        : "?" + request.getURI().getRawQuery());
                            h.set(
                                io.github.coreplatform.common.security.gateway.GatewayContextCodec
                                    .HEADER,
                                signers
                                    .forService(audience)
                                    .sign(
                                        identity.actor(),
                                        identity.roleId(),
                                        request.getMethod().name(),
                                        target));
                            h.set(SecurityHeaders.X_USER_ID, identity.userId().toString());
                            if (identity.tenantId() != null)
                              h.set(SecurityHeaders.X_TENANT_ID, identity.tenantId().toString());
                          });
              return chain.filter(exchange.mutate().request(builder.build()).build());
            });
  }

  private String accessCookie(ServerHttpRequest request) {
    var cookies = request.getCookies().get(AuthCookies.ACCESS_TOKEN);
    return cookies != null && cookies.size() == 1 ? cookies.getFirst().getValue() : null;
  }

  private ServerHttpRequest.Builder forwardCookies(ServerHttpRequest request, String path) {
    var values = new java.util.ArrayList<String>();
    boolean logout = path.equals(ApiPaths.V1 + "/auth/logout");
    if (path.equals(ApiPaths.V1 + "/auth/refresh")
        || path.equals(ApiPaths.V1 + "/auth/switch-tenant")
        || logout) {
      var refresh = request.getCookies().get(AuthCookies.REFRESH_TOKEN);
      if (refresh != null && refresh.size() == 1)
        values.add(AuthCookies.REFRESH_TOKEN + "=" + refresh.getFirst().getValue());
    }
    if (logout) {
      String access = accessCookie(request);
      if (access != null) values.add(AuthCookies.ACCESS_TOKEN + "=" + access);
    }
    return request
        .mutate()
        .headers(
            headers -> {
              headers.remove(HttpHeaders.COOKIE);
              headers.remove(HttpHeaders.AUTHORIZATION);
              if (!values.isEmpty()) headers.set(HttpHeaders.COOKIE, String.join("; ", values));
            });
  }

  /**
   * Nhóm route cho phép Identity chưa gắn tenant, ví dụ quản lý tài khoản, tenant, CMS và lời mời.
   * Vẫn phải xác thực JWT nếu route không thuộc publicPath(). Service đích chịu trách nhiệm kiểm
   * tra quyền thao tác cụ thể.
   */
  private boolean identityRoute(String path) {
    return path.startsWith(ApiPaths.V1 + "/auth/")
        || path.equals(ApiPaths.V1 + "/tenants")
        || path.startsWith(ApiPaths.V1 + "/tenants/")
        || path.startsWith(ApiPaths.V1 + "/cms/")
        || path.startsWith(ApiPaths.V1 + "/invitations/");
  }

  /**
   * Các đường dẫn không yêu cầu JWT tại filter này. Auth/health khớp chính xác; OpenAPI khớp theo
   * tiền tố. Request vẫn phải qua bước xóa header và kiểm tra Origin ở trên.
   */
  private boolean publicPath(String path) {
    return Set.of(
                ApiPaths.V1 + "/auth/login",
                ApiPaths.V1 + "/auth/register",
                ApiPaths.V1 + "/auth/refresh",
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness")
            .contains(path)
        || path.startsWith("/docs")
        || path.startsWith("/scalar")
        || path.startsWith("/v3/api-docs");
  }

  /** Chọn mã lỗi mặc định: 401 thiếu/sai xác thực, 403 bị từ chối, còn lại lỗi khả dụng. */
  private Mono<Void> error(ServerWebExchange exchange, int status) {
    return error(
        exchange,
        status,
        status == 401
            ? "unauthorized"
            : status == 403 ? "forbidden" : "authentication-unavailable");
  }

  /** Ghi phản hồi lỗi dạng application/problem+json và kết thúc, không gọi chain.filter(). */
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
