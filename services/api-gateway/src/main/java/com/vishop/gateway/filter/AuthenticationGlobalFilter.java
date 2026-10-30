package com.vishop.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import com.vishop.gateway.security.AuthenticationFailure;
import com.vishop.gateway.security.GatewayContextSigners;
import com.vishop.gateway.security.LocalAuthenticationVerifier;
import com.vishop.gateway.security.LocalAuthenticationVerifier.Identity;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Authenticates at ingress and propagates request-bound, service-specific
 * trusted context.
 */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {
  private final LocalAuthenticationVerifier authentication;
  private final GatewayContextSigners signers;

  private final Set<String> origins;

  /**
   * Nhận danh sách Origin phân cách bằng dấu phẩy từ cấu hình. Nếu chưa cấu hình,
   * mặc định cho phép
   * http://localhost:3000.
   */
  public AuthenticationGlobalFilter(
      LocalAuthenticationVerifier authentication,
      GatewayContextSigners signers,
      @Value("${vishop.security.allowed-origins:http://localhost:3000,http://localhost:8000}") String origins) {
    this.authentication = authentication;
    this.signers = signers;
    this.origins = Arrays.stream(origins.split(",")).map(String::trim).collect(Collectors.toSet());
  }

  @Override
  public Mono<Void> filter(ServerWebExchange original, GatewayFilterChain chain) {
    ServerHttpRequest request = removeUntrustedHeaders(original.getRequest());
    ServerWebExchange exchange = original.mutate().request(request).build();

    if (!allowedOrigin(request))
      return error(exchange, 403);
    if (!validCsrfHeader(request))
      return error(exchange, 403, "csrf-request-header-required");
    if (allowsAnonymous(request))
      return forwardWithoutContext(exchange, chain);

    return authenticateAndForward(exchange, chain);
  }

  /** Xóa header định danh từ client; chỉ gateway được tạo context tin cậy. */
  private ServerHttpRequest removeUntrustedHeaders(ServerHttpRequest request) {
    return request
        .mutate()
        .headers(
            headers -> headers.keySet().stream()
                .filter(this::isUntrustedHeader)
                .toList()
                .forEach(headers::remove))
        .build();
  }

  private boolean isUntrustedHeader(String name) {
    String normalized = name.toLowerCase(java.util.Locale.ROOT);
    return normalized.startsWith("x-user-")
        || normalized.startsWith("x-gateway-")
        || name.equalsIgnoreCase(SecurityHeaders.X_TENANT_ID)
        || name.equalsIgnoreCase("X-Iam-Internal-Key");
  }

  private boolean allowedOrigin(ServerHttpRequest request) {
    String origin = request.getHeaders().getOrigin();
    return origin == null || origins.contains(origin);
  }

  private boolean validCsrfHeader(ServerHttpRequest request) {
    if (isSepayWebhook(request))
      return true;
    if (!AuthCookies.requiresRequestHeader(request.getMethod().name()))
      return true;
    return AuthCookies.REQUEST_HEADER_VALUE.equals(
        request.getHeaders().getFirst(AuthCookies.REQUEST_HEADER));
  }

  private boolean isSepayWebhook(ServerHttpRequest request) {
    return request.getMethod() == HttpMethod.POST
        && request.getURI().getRawPath().equals(ApiPaths.V1 + "/billing/webhooks/sepay");
  }

  private boolean allowsAnonymous(ServerHttpRequest request) {
    return request.getMethod() == HttpMethod.OPTIONS
        || isSepayWebhook(request)
        || publicPath(request.getURI().getRawPath());
  }

  private Mono<Void> authenticateAndForward(ServerWebExchange exchange, GatewayFilterChain chain) {
    var request = exchange.getRequest();
    String credential = accessCredential(request);
    if (credential == null || credential.isBlank()) {
      if (isLogout(request))
        return forwardWithoutContext(exchange, chain);
      return error(exchange, 401);
    }

    // Chỉ chuyển lỗi xác thực thành signal, tránh bắt nhầm lỗi từ service phía sau.
    return authentication
        .authenticate(credential, true)
        .materialize()
        .flatMap(
            signal -> {
              if (signal.isOnError())
                return authenticationError(exchange, chain, signal.getThrowable());
              Identity identity = signal.get();
              if (identity == null)
                return error(exchange, 503);
              return forwardWithContext(exchange, chain, identity);
            });
  }

  private String accessCredential(ServerHttpRequest request) {
    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authorization == null)
      return accessCookie(request);
    if (!authorization.startsWith("Bearer "))
      return null;
    return authorization.substring(7).trim();
  }

  private boolean isLogout(ServerHttpRequest request) {
    return request.getURI().getRawPath().equals(ApiPaths.V1 + "/auth/logout");
  }

  private Mono<Void> authenticationError(
      ServerWebExchange exchange, GatewayFilterChain chain, Throwable failure) {
    // Token không hợp lệ vẫn cho logout đến IAM để xóa cookie.
    if (isLogout(exchange.getRequest()))
      return forwardWithoutContext(exchange, chain);
    if (failure instanceof AuthenticationFailure rejected)
      return error(exchange, rejected.status(), rejected.code());
    return error(exchange, 503);
  }

  private Mono<Void> forwardWithoutContext(ServerWebExchange exchange, GatewayFilterChain chain) {
    var request = exchange.getRequest();
    var forwarded = forwardCookies(request, request.getURI().getRawPath()).build();
    return chain.filter(exchange.mutate().request(forwarded).build());
  }

  private Mono<Void> forwardWithContext(
      ServerWebExchange exchange, GatewayFilterChain chain, Identity identity) {
    var request = exchange.getRequest();
    String path = request.getURI().getRawPath();
    if (identity.tenantId() == null && !identityRoute(path))
      return error(exchange, 403);

    String context = signContext(exchange, identity);
    var forwarded = forwardCookies(request, path)
        .headers(
            headers -> {
              headers.set(GatewayContextCodec.HEADER, context);
              headers.set(SecurityHeaders.X_USER_ID, identity.userId().toString());
              if (identity.tenantId() != null)
                headers.set(SecurityHeaders.X_TENANT_ID, identity.tenantId().toString());
            })
        .build();
    return chain.filter(exchange.mutate().request(forwarded).build());
  }

  private String signContext(ServerWebExchange exchange, Identity identity) {
    var request = exchange.getRequest();
    String target = request.getURI().getRawPath();
    String query = request.getURI().getRawQuery();
    if (query != null)
      target += "?" + query;
    return signers
        .forService(targetService(exchange))
        .sign(identity.actor(), identity.roleId(), request.getMethod().name(), target);
  }

  private String targetService(ServerWebExchange exchange) {
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    if (route == null)
      return "iam-service";
    if (route.getId().equals("cms-audit-service"))
      return "audit-service";
    return route.getId();
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
      if (access != null)
        values.add(AuthCookies.ACCESS_TOKEN + "=" + access);
    }
    return request
        .mutate()
        .headers(
            headers -> {
              headers.remove(HttpHeaders.COOKIE);
              headers.remove(HttpHeaders.AUTHORIZATION);
              if (!values.isEmpty())
                headers.set(HttpHeaders.COOKIE, String.join("; ", values));
            });
  }

  /**
   * Nhóm route cho phép Identity chưa gắn tenant, ví dụ quản lý tài khoản,
   * tenant, CMS và lời mời.
   * Vẫn phải xác thực JWT nếu route không thuộc publicPath(). Service đích chịu
   * trách nhiệm kiểm
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
   * Các đường dẫn không yêu cầu JWT tại filter này. Auth/health khớp chính xác;
   * OpenAPI khớp theo
   * tiền tố. Request vẫn phải qua bước xóa header và kiểm tra Origin ở trên.
   */
  private boolean publicPath(String path) {
    return Set.of(
        ApiPaths.V1 + "/auth/login",
        ApiPaths.V1 + "/auth/register",
        ApiPaths.V1 + "/auth/refresh",
                "/.well-known/jwks.json",
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness")
        .contains(path)
        || path.startsWith("/docs")
        || path.startsWith("/scalar")
        || path.startsWith("/v3/api-docs");
  }

  /**
   * Chọn mã lỗi mặc định: 401 thiếu/sai xác thực, 403 bị từ chối, còn lại lỗi khả
   * dụng.
   */
  private Mono<Void> error(ServerWebExchange exchange, int status) {
    return error(
        exchange,
        status,
        status == 401
            ? "unauthorized"
            : status == 403 ? "forbidden" : "authentication-unavailable");
  }

  /**
   * Ghi phản hồi lỗi dạng application/problem+json và kết thúc, không gọi
   * chain.filter().
   */
  private Mono<Void> error(ServerWebExchange exchange, int status, String code) {
    var response = exchange.getResponse();
    response.setStatusCode(HttpStatusCode.valueOf(status));
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String title = status == 401 ? "Unauthorized" : status == 403 ? "Forbidden" : "Authentication unavailable";
    byte[] json = ("{\"type\":\"/errors/"
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
