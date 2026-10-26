package com.vishop.gateway.filter;

import com.vishop.gateway.security.AuthenticationFailure;
import com.vishop.gateway.security.LocalAuthenticationVerifier;
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

/**
 * Filter xác thực dùng chung cho các route đi qua Spring Cloud Gateway.
 *
 * <p>Luồng xử lý: xóa header định danh do client gửi → kiểm tra Origin → bỏ qua xác thực cho
 * OPTIONS/public path → xác minh access JWT từ cookie/Bearer → kiểm tra tenant → chuyển tiếp
 * request.
 *
 * <p>Xác thực stateless: verifier kiểm tra access JWT tại chỗ, không tra session hay database hay
 * Redis. Header định danh gửi xuống service được dựng lại từ JWT đã xác minh; client không thể tự
 * cấp quyền bằng cách gửi X-User-Id hoặc X-Tenant-Id.
 */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {
  // Thành phần xác minh JWT và trả về userId, tenantId cùng access token đã kiểm tra.
  private final LocalAuthenticationVerifier authentication;

  // Danh sách Origin được phép; chỉ áp dụng khi request có header Origin.
  private final Set<String> origins;

  /**
   * Nhận danh sách Origin phân cách bằng dấu phẩy từ cấu hình. Nếu chưa cấu hình, mặc định cho phép
   * http://localhost:3000.
   */
  public AuthenticationGlobalFilter(
      LocalAuthenticationVerifier authentication,
      @Value("${vishop.security.allowed-origins:http://localhost:3000,http://localhost:8000}")
          String origins) {
    this.authentication = authentication;
    // Bỏ khoảng trắng quanh mỗi giá trị; Set loại trùng và hỗ trợ kiểm tra contains().
    this.origins = Arrays.stream(origins.split(",")).map(String::trim).collect(Collectors.toSet());
  }

  @Override
  public Mono<Void> filter(ServerWebExchange original, GatewayFilterChain chain) {
    // 1. Request trong WebFlux được thay đổi thông qua builder mutate().
    // Xóa header định danh/nội bộ ngay cả với public path để tránh giả mạo từ client.
    ServerHttpRequest request =
        original
            .getRequest()
            .mutate()
            .headers(
                h -> {
                  // So sánh không phân biệt hoa/thường. Locale.ROOT giúp việc đổi chữ
                  // thường không phụ thuộc locale của máy chủ.
                  h.keySet().stream()
                      .filter(
                          key ->
                              key.toLowerCase(java.util.Locale.ROOT).startsWith("x-user-")
                                  || key.equalsIgnoreCase(SecurityHeaders.X_TENANT_ID)
                                  || key.equalsIgnoreCase("X-Iam-Internal-Key"))
                      // Chụp danh sách tên trước khi xóa, tránh vừa duyệt vừa sửa keySet.
                      .toList()
                      .forEach(h::remove);
                })
            .build();
    // Dùng request đã làm sạch cho mọi nhánh xử lý tiếp theo.
    ServerWebExchange exchange = original.mutate().request(request).build();
    // Raw path giữ nguyên phần ký tự percent-encoded; các rule bên dưới so khớp giá trị này.
    String path = request.getURI().getRawPath();
    String origin = request.getHeaders().getOrigin();
    // 2. Nếu có Origin thì phải khớp chính xác cấu hình. Không có Origin vẫn đi tiếp.
    // Kiểm tra này diễn ra trước nhánh OPTIONS/public; đây chưa phải toàn bộ xử lý CORS.
    if (origin != null && !origins.contains(origin)) return error(exchange, 403);
    if (AuthCookies.requiresRequestHeader(request.getMethod().name())
        && !AuthCookies.REQUEST_HEADER_VALUE.equals(
            request.getHeaders().getFirst(AuthCookies.REQUEST_HEADER)))
      return error(exchange, 403, "csrf-request-header-required");
    // 3. Cho OPTIONS (thường dùng cho CORS preflight) và public path đi tiếp không cần JWT.
    // chain.filter() chuyển quyền xử lý cho filter kế tiếp trong chuỗi.
    if (request.getMethod() == HttpMethod.OPTIONS || publicPath(path))
      return chain.filter(exchange.mutate().request(forwardCookies(request, path).build()).build());

    // 4. Trình duyệt dùng access cookie HttpOnly; Bearer vẫn phục vụ lời gọi nội bộ.
    // Nếu có Authorization thì phải hợp lệ, không fallback từ header sai sang cookie.
    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    String credential =
        authorization != null
            ? authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null
            : accessCookie(request);
    // Thiếu header, sai tiền tố hoặc token rỗng: kết thúc request với 401.
    if (credential == null || credential.isBlank()) return error(exchange, 401);

    // 5. true cho biết credential được lấy theo cơ chế Bearer.
    // Mono mô tả xử lý reactive; không cần gọi block() hay subscribe() thủ công.
    return authentication
        .authenticate(credential, true)
        // Chuyển kết quả/lỗi/hoàn tất rỗng thành Signal để xử lý trong cùng flatMap.
        // Chỉ materialize kết quả xác thực; lỗi từ chain.filter() phía dưới không bị
        // nhánh này chuyển nhầm thành lỗi xác thực.
        .materialize()
        .flatMap(
            signal -> {
              if (signal.isOnError()) {
                var failure = signal.getThrowable();
                // Lỗi xác thực đã biết: giữ status/code do verifier cung cấp.
                // Lỗi ngoài dự kiến: trả 503, không đưa chi tiết exception cho client.
                return failure instanceof AuthenticationFailure rejected
                    ? error(exchange, rejected.status(), rejected.code())
                    : error(exchange, 503);
              }
              var identity = signal.get();
              // Verifier hoàn tất mà không trả Identity được xem là không khả dụng.
              if (identity == null) return error(exchange, 503);
              // 6. Token chưa có tenant chỉ được vào nhóm route identityRoute().
              // Các route còn lại yêu cầu tenantId; thiếu tenant thì trả 403.
              if (identity.tenantId() == null && !identityRoute(path)) return error(exchange, 403);
              // 7. Chuẩn bị request gửi xuống service sau khi xác thực thành công.
              var builder =
                  forwardCookies(request, path)
                      .headers(
                          h -> {
                            // Ghi lại token đã xác minh và dựng định danh từ claims tin cậy.
                            h.setBearerAuth(identity.accessToken());
                            h.set(SecurityHeaders.X_USER_ID, identity.userId().toString());
                            if (identity.tenantId() != null)
                              h.set(SecurityHeaders.X_TENANT_ID, identity.tenantId().toString());
                          });
              // Chuyển exchange chứa request mới cho phần còn lại của gateway pipeline.
              return chain.filter(exchange.mutate().request(builder.build()).build());
            });
  }

  private String accessCookie(ServerHttpRequest request) {
    var cookies = request.getCookies().get(AuthCookies.ACCESS_TOKEN);
    return cookies != null && cookies.size() == 1 ? cookies.getFirst().getValue() : null;
  }

  private ServerHttpRequest.Builder forwardCookies(ServerHttpRequest request, String path) {
    // Only IAM auth endpoints receive refresh credentials. Other services receive Bearer access
    // only.
    // Resolve lazy cookie parsing before removing the request's Cookie header.
    var values = new java.util.ArrayList<String>();
    boolean logout = path.equals("/api/v1/auth/logout");
    if (path.equals("/api/v1/auth/refresh")
        || path.equals("/api/v1/auth/switch-tenant")
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
              if (!values.isEmpty()) headers.set(HttpHeaders.COOKIE, String.join("; ", values));
            });
  }

  /**
   * Nhóm route cho phép Identity chưa gắn tenant, ví dụ quản lý tài khoản, tenant, CMS và lời mời.
   * Vẫn phải xác thực JWT nếu route không thuộc publicPath(). Service đích chịu trách nhiệm kiểm
   * tra quyền thao tác cụ thể.
   */
  private boolean identityRoute(String path) {
    return path.startsWith("/api/v1/auth/")
        || path.equals("/api/v1/tenants")
        || path.startsWith("/api/v1/tenants/")
        || path.startsWith("/api/v1/cms/")
        || path.startsWith("/api/v1/invitations/");
  }

  /**
   * Các đường dẫn không yêu cầu JWT tại filter này. Auth/health khớp chính xác; OpenAPI khớp theo
   * tiền tố. Request vẫn phải qua bước xóa header và kiểm tra Origin ở trên.
   */
  private boolean publicPath(String path) {
    return Set.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
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
    // Body gồm type (đường dẫn mã lỗi), title và HTTP status, mã hóa UTF-8.
    byte[] json =
        ("{\"type\":\"/errors/"
                + code
                + "\",\"title\":\""
                + title
                + "\",\"status\":"
                + status
                + "}")
            .getBytes(StandardCharsets.UTF_8);
    // Bọc byte[] thành DataBuffer để WebFlux ghi response theo cơ chế reactive.
    return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
  }

  @Override
  public int getOrder() {
    // Giá trị order càng nhỏ thì filter càng chạy sớm ở chiều request.
    // Chạy gần mức ưu tiên cao nhất; thứ tự tương đối phụ thuộc order của filter khác.
    return Ordered.HIGHEST_PRECEDENCE + 2;
  }
}
