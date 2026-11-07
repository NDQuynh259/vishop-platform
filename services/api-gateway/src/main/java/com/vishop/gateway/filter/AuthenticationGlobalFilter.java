package com.vishop.gateway.filter;

import com.vishop.gateway.handler.AuthenticationErrorResponse;
import com.vishop.gateway.security.GatewayRequestForwarder;
import com.vishop.gateway.security.GatewayRequestPolicy;
import com.vishop.gateway.security.LocalAuthenticationVerifier;
import com.vishop.gateway.security.LocalAuthenticationVerifier.Identity;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

/** Điều phối: làm sạch header → kiểm tra policy → xác thực access → chuyển tiếp request. */
@Component
@RequiredArgsConstructor
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {
  private final LocalAuthenticationVerifier authentication;
  private final GatewayRequestPolicy policy;
  private final GatewayRequestForwarder forwarder;
  private final AuthenticationErrorResponse errors;

  // #region filter
  @Override
  public Mono<Void> filter(ServerWebExchange original, GatewayFilterChain chain) {
    // 1. Xóa các header không đáng tin cậy, tránh bị spoofing.
    var exchange = forwarder.removeUntrustedHeaders(original);
    var request = exchange.getRequest();

    // 2. Kiểm tra Origin được cho phép
    if (!policy.allowsOrigin(request)) return errors.write(exchange, 403);

    // 3. Kiểm tra CSRF header (hoặc là webhook Sepay)
    if (!policy.hasRequiredCsrfHeader(request))
      return errors.write(exchange, 403, "csrf-request-header-required");

    // 4. Chuyển tiếp route public mà không yêu cầu access token.
    if (policy.allowsAnonymous(request)) return forwarder.forwardWithoutContext(exchange, chain);

    // 5. Xác thực access token và chuyển tiếp request.
    return authenticateAndForward(exchange, chain);
  }

  // #region authenticateAndForward
  private Mono<Void> authenticateAndForward(ServerWebExchange exchange, GatewayFilterChain chain) {
    String accessToken = forwarder.accessToken(exchange.getRequest());
    if (accessToken == null || accessToken.isBlank()) {
      if (policy.isLogout(exchange.getRequest()))
        return forwarder.forwardWithoutContext(exchange, chain);
      return errors.write(exchange, 401);
    }

    // materialize chỉ bắt kết quả/lỗi xác thực, trước khi gọi chain.
    // Lỗi từ service phía sau không được biến thành lỗi token hoặc retry logout.
    return authentication
        .authenticate(accessToken, true)
        .materialize()
        .flatMap(result -> handleAuthenticationResult(exchange, chain, result));
  }

  // #region handleAuthenticationResult
  private Mono<Void> handleAuthenticationResult(
      ServerWebExchange exchange, GatewayFilterChain chain, Signal<Identity> result) {
    if (result.isOnError()) return authenticationError(exchange, chain, result.getThrowable());
    Identity identity = result.get();
    if (identity == null) return errors.write(exchange, 503);
    if (policy.isCms(exchange.getRequest())
        && !SystemRole.PLATFORM_ADMIN.name().equals(identity.actor().systemRole()))
      return errors.write(exchange, 403);
    if (identity.tenantId() == null && !policy.allowsWithoutTenant(exchange.getRequest()))
      return errors.write(exchange, 403);

    return forwarder.forwardWithContext(exchange, chain, identity);
  }

  // #region authenticationError
  private Mono<Void> authenticationError(
      ServerWebExchange exchange, GatewayFilterChain chain, Throwable failure) {
    // Access thiếu/hết hạn vẫn cho IAM nhận refresh cookie để logout phiên.
    if (policy.isLogout(exchange.getRequest()))
      return forwarder.forwardWithoutContext(exchange, chain);
    if (failure instanceof UnauthorizedException rejected)
      return errors.write(exchange, 401, rejected.getCode());
    if (failure instanceof ForbiddenException rejected)
      return errors.write(exchange, 403, rejected.getCode());
    return errors.write(exchange, 503);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 2;
  }
}
