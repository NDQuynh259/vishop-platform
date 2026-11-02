package com.vishop.gateway.security;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.security.constants.AuthCookies;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Các quy tắc HTTP: Origin/CSRF, route public, tenant và endpoint nhận refresh
 * cookie.
 */
@Component
public class GatewayRequestPolicy {
  private final Set<String> origins;

  public GatewayRequestPolicy(
      @Value("${vishop.security.allowed-origins:http://localhost:3000,http://localhost:8000}") String origins) {
    this.origins = Arrays.stream(origins.split(",")).map(String::trim).collect(Collectors.toSet());
  }

  // #region allowsOrigin
  public boolean allowsOrigin(ServerHttpRequest request) {
    String origin = request.getHeaders().getOrigin();
    return origin == null || origins.contains(origin);
  }

  /**
   * Webhook Sepay không cần CSRF header, các request khác phải có header
   * "X-Requested-With: XMLHttpRequest" (hoặc không cần nếu method không yêu cầu
   * CSRF).
   *
   * @param request HTTP request từ client.
   * @return true nếu request có header CSRF hợp lệ hoặc không cần CSRF.
   */
  public boolean hasRequiredCsrfHeader(ServerHttpRequest request) {
    if (isSepayWebhook(request))
      return true;
    if (!AuthCookies.requiresRequestHeader(request.getMethod().name()))
      return true;
    return AuthCookies.REQUEST_HEADER_VALUE.equals(
        request.getHeaders().getFirst(AuthCookies.REQUEST_HEADER));
  }

  /**
   * Các route public không cần xác thực access token.
   *
   * @param request HTTP request từ client.
   * @return true nếu request là public.
   */
  public boolean allowsAnonymous(ServerHttpRequest request) {
    return request.getMethod() == HttpMethod.OPTIONS
        || isSepayWebhook(request)
        || publicPath(request.getURI().getRawPath());
  }

  /**
   * Logout không cần access token, nhưng vẫn cần CSRF header (hoặc là webhook
   * Sepay).
   *
   * @param request HTTP request từ client.
   * @return true nếu request là logout.
   */
  public boolean isLogout(ServerHttpRequest request) {
    return request.getURI().getRawPath().equals(ApiPaths.V1 + "/auth/logout");
  }

  /**
   * Identity chưa chọn tenant vẫn dùng được các API này; service đích kiểm tra
   * quyền cụ thể.
   */
  public boolean allowsWithoutTenant(ServerHttpRequest request) {
    String path = request.getURI().getRawPath();
    return path.startsWith(ApiPaths.V1 + "/auth/")
        || path.equals(ApiPaths.V1 + "/tenants")
        || path.startsWith(ApiPaths.V1 + "/tenants/")
        || path.startsWith(ApiPaths.V1 + "/cms/")
        || path.startsWith(ApiPaths.V1 + "/invitations/");
  }

  // #region canForwardRefreshToken
  /**
   * Chỉ cho phép refresh cookie forward đến IAM service, không cho các service
   * khác.
   */
  public boolean canForwardRefreshToken(ServerWebExchange exchange) {
    var request = exchange.getRequest();
    return "iam-service".equals(targetService(exchange))
        && request.getMethod() == HttpMethod.POST
        && refreshTokenPath(request.getURI().getRawPath());
  }

  /**
   * Route CMS audit có ID riêng nhưng vẫn nhận context dành cho audit-service.
   */
  public String targetService(ServerWebExchange exchange) {
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    if (route == null)
      return "iam-service";
    if (route.getId().equals("cms-audit-service"))
      return "audit-service";
    return route.getId();
  }

  private boolean isSepayWebhook(ServerHttpRequest request) {
    return request.getMethod() == HttpMethod.POST
        && request.getURI().getRawPath().equals(ApiPaths.V1 + "/billing/webhooks/sepay");
  }

  // #region refreshTokenPath
  private boolean refreshTokenPath(String path) {
    return Set.of(
        ApiPaths.V1 + "/auth/refresh",
        ApiPaths.V1 + "/auth/logout")
        .contains(path);
  }

  // #region publicPath
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
}
