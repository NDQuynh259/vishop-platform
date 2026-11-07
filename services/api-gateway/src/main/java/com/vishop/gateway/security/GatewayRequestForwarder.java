package com.vishop.gateway.security;

import com.vishop.gateway.security.LocalAuthenticationVerifier.Identity;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.gateway.GatewayIdentityHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Overwrites client identity headers; downstream ingress must be restricted to this gateway. */
@Component
public class GatewayRequestForwarder {
  private final GatewayRequestPolicy policy;

  public GatewayRequestForwarder(GatewayRequestPolicy policy) {
    this.policy = policy;
  }

  public ServerWebExchange removeUntrustedHeaders(ServerWebExchange exchange) {
    var request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers ->
                    headers.keySet().stream()
                        .filter(SecurityHeaders::isInternalHeader)
                        .toList()
                        .forEach(headers::remove))
            .build();
    return exchange.mutate().request(request).build();
  }

  public String accessToken(ServerHttpRequest request) {
    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authorization == null) return cookieValue(request, AuthCookies.ACCESS_TOKEN);
    return authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
  }

  public Mono<Void> forwardWithoutContext(ServerWebExchange exchange, GatewayFilterChain chain) {
    var request = prepareCredentials(exchange).build();
    return chain.filter(exchange.mutate().request(request).build());
  }

  public Mono<Void> forwardWithContext(
      ServerWebExchange exchange, GatewayFilterChain chain, Identity identity) {
    var actor = identity.actor();
    var headers =
        new GatewayIdentityHeaders(
                actor.userId(),
                actor.sessionId(),
                actor.tenantId(),
                identity.roleId(),
                actor.systemRole())
            .toHeaders();
    var request = prepareCredentials(exchange).headers(target -> target.putAll(headers)).build();
    return chain.filter(exchange.mutate().request(request).build());
  }

  // #region prepareCredentials

  /**
   * Lọc credential trước khi gateway chuyển request xuống service đích.
   *
   * <p>Luôn xóa Authorization và toàn bộ Cookie gốc để backend không nhận access JWT, refresh token
   * hoặc cookie trình duyệt không liên quan. Danh tính đã xác thực được truyền riêng qua đúng năm
   * header X-User-Id, X-Session-Id, X-Tenant-Id, X-Role-Id và X-System-Role.
   *
   * <p>Ngoại lệ: chỉ POST /api/v1/auth/refresh và POST /api/v1/auth/logout trên route iam-service
   * được nhận lại refresh cookie. IAM cần token này để cấp access mới hoặc thu hồi phiên khi access
   * token không còn hợp lệ.
   *
   * <p>Hàm chỉ lọc header, không xác thực refresh token và không thay đổi body, method hoặc path.
   * IAM chịu trách nhiệm xác thực refresh token.
   *
   * @param exchange request hiện tại và thông tin route do gateway xác định
   * @return builder để phía gọi gắn thêm header danh tính trước khi gọi build()
   */
  private ServerHttpRequest.Builder prepareCredentials(ServerWebExchange exchange) {
    var request = exchange.getRequest();

    // Kiểm tra đủ route IAM + method POST + path refresh/logout trước khi lấy refresh.
    // Phải đọc từ request gốc vì Cookie sẽ bị xóa khỏi request chuyển tiếp bên dưới.
    String refresh =
        policy.canForwardRefreshToken(exchange)
            ? cookieValue(request, AuthCookies.REFRESH_TOKEN)
            : null;

    // Request là immutable. mutate() tạo builder; chưa thay request gốc trong exchange.
    return request
        .mutate()
        .headers(
            headers -> {
              // Xóa tất cả cookie client gửi, kể cả access_token và refresh_token.
              // Không chuyển nguyên bộ cookie trình duyệt xuống service nghiệp vụ.
              headers.remove(HttpHeaders.COOKIE);

              // Access JWT dừng tại gateway; backend nhận danh tính qua header nội bộ.
              headers.remove(HttpHeaders.AUTHORIZATION);

              // Nếu policy cho phép và có đúng một refresh cookie, dựng lại Cookie mới
              // chỉ chứa refresh_token. set() thay thế header, không nối cookie cũ.
              if (refresh != null)
                headers.set(HttpHeaders.COOKIE, AuthCookies.REFRESH_TOKEN + "=" + refresh);
            });
  }

  // #endregion

  /**
   * Đọc cookie theo tên khi request có đúng một cookie mang tên đó.
   *
   * <p>Cookie cùng tên có thể xuất hiện nhiều lần, ví dụ được đặt với các Path khác nhau. Khi bị
   * trùng, không tự chọn cookie đầu hoặc cuối vì có thể chọn nhầm credential. Trả null để phía gọi
   * xử lý như không tìm được credential duy nhất.
   *
   * <p>Helper dùng cho cả access và refresh. Nó trả nguyên giá trị cookie, không kiểm tra chữ ký,
   * thời hạn hay nội dung JWT; giá trị rỗng cũng không tự chuyển thành null.
   *
   * @param request request đầu vào cần đọc cookie
   * @param name tên cookie, ví dụ access_token hoặc refresh_token
   * @return giá trị cookie duy nhất; null nếu không có hoặc có nhiều cookie cùng tên
   */
  private String cookieValue(ServerHttpRequest request, String name) {
    var cookies = request.getCookies().get(name);

    // Chỉ lấy phần tử đầu sau khi đã xác nhận danh sách có đúng một phần tử.
    return cookies != null && cookies.size() == 1 ? cookies.getFirst().getValue() : null;
  }
}
