package com.vishop.gateway.security;

import com.vishop.gateway.security.LocalAuthenticationVerifier.Identity;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * =========================================================================================
 * COMPONENT: GatewayRequestForwarder
 * =========================================================================================
 * Đảm
 * nhiệm vai trò chuẩn bị và bảo vệ request trước khi chuyển tiếp (forward)
 * xuống các microservice
 * downstream trong kiến trúc Spring Cloud Gateway.
 *
 * <p>
 * Các nhiệm vụ bảo mật cốt lõi: 1. Chống giả mạo danh tính (Header
 * Sanitization): Loại bỏ toàn
 * bộ header nhạy cảm do client tự ý gửi lên. 2. Trích xuất Credential: Đọc
 * Access Token ưu tiên từ
 * header Authorization Bearer, fallback qua Cookie. 3. Cô lập Credential
 * (Credential Stripping):
 * Không chuyển tiếp token gốc xuống service nội bộ để chống rò rỉ. 4. Ký số Ngữ
 * cảnh (Cryptographic
 * Context Signing): Dùng HMAC/Chữ ký số ký đóng gói danh tính (userId,
 * tenantId, role, path,
 * method) để các downstream service hoàn toàn tin tưởng mà không cần query lại
 * database. 5. Bảo vệ
 * Refresh Token: Chỉ duy nhất route refresh/logout của IAM mới được phép nhận
 * Refresh Token.
 * =========================================================================================
 */
@Component
public class GatewayRequestForwarder {

  /**
   * Quản lý các bộ ký số ngữ cảnh (Context Signer) riêng biệt cho từng service
   * đích.
   */
  private final GatewayContextSigners signers;

  /**
   * Định nghĩa chính sách định tuyến: service đích là gì, route nào được phép
   * chuyển Refresh Token.
   */
  private final GatewayRequestPolicy policy;

  public GatewayRequestForwarder(GatewayContextSigners signers, GatewayRequestPolicy policy) {
    this.signers = signers;
    this.policy = policy;
  }

  /**
   * BƯỚC 1: LOẠI BỎ HEADER KHÔNG ĐÁNG TIN CẬY (UNTRUSTED HEADER SANITIZATION)
   *
   * <p>
   * Nguyên lý bảo mật: Client từ ngoài Internet tuyệt đối KHÔNG ĐƯỢC tự khai báo
   * danh tính (như
   * x-user-id, x-tenant-id) hoặc các header nội bộ trước khi Gateway hoàn tất
   * việc xác thực. Nếu
   * client cố tình gửi các header này nhằm mục đích mạo danh (Header Injection /
   * Identity
   * Spoofing), Gateway sẽ lọc bỏ sạch sẽ tại đây.
   *
   * @param exchange Ngữ cảnh HTTP request/response hiện tại của Gateway.
   * @return ServerWebExchange mới với request đã được gỡ bỏ các header không an
   *         toàn.
   */
  // #region removeUntrustedHeaders
  public ServerWebExchange removeUntrustedHeaders(ServerWebExchange exchange) {
    var request = exchange
        .getRequest()
        .mutate()
        .headers(
            headers -> headers.keySet().stream()
                // Lọc ra các header thuộc danh sách cấm / nghi vấn
                .filter(SecurityHeaders::isInternalHeader)
                .toList()
                // Xóa bỏ hoàn toàn khỏi request trước khi xử lý tiếp
                .forEach(headers::remove))
        .build();
    return exchange.mutate().request(request).build();
  }

  /**
   * BƯỚC 2: TRÍCH XUẤT ACCESS TOKEN TỪ REQUEST ĐẦU VÀO
   *
   * <p>
   * Quy tắc ưu tiên: 1. Ưu tiên cao nhất: Header "Authorization: Bearer <token>"
   * (chuẩn REST API
   * / Mobile / Postman). 2. Nếu không có header Authorization: Fallback sang đọc
   * Cookie tên
   * "access_token" (chuẩn Web SPA / Dashboard). 3. Ràng buộc an toàn: Nếu client
   * gửi header
   * Authorization nhưng SAI ĐỊNH DẠNG (không bắt đầu bằng "Bearer "), hệ thống
   * TRẢ VỀ NULL NGAY LẬP
   * TỨC chứ KHÔNG fallback sang cookie (tránh việc client gửi header lỗi nhưng vô
   * tình nhận diện
   * nhầm theo phiên cookie cũ).
   *
   * @param request HTTP request từ client.
   * @return Chuỗi Access Token hợp lệ, hoặc null nếu không tìm thấy/sai format.
   */
  // #region accessToken
  public String accessToken(ServerHttpRequest request) {
    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    // Trường hợp 1: Không có header Authorization -> Tìm trong Cookie
    if (authorization == null) {
      return cookieValue(request, AuthCookies.ACCESS_TOKEN);
    }

    // Trường hợp 2: Có header nhưng không đúng định dạng "Bearer " -> Coi như không hợp lệ
    if (!authorization.startsWith("Bearer ")) {
      return null;
    }

    // Trường hợp 3: Cắt bỏ tiền tố "Bearer " (7 ký tự) để lấy chuỗi token tinh khiết
    return authorization.substring(7).trim();
  }

  /**
   * BƯỚC 3A: CHUYỂN TIẾP REQUEST KHÔNG CẦN NGỮ CẢNH (PUBLIC ENDPOINTS)
   *
   * <p>
   * Sử dụng cho các API công khai không yêu cầu đăng nhập (Public APIs như
   * /login, /register,
   * /docs). Vẫn gọi prepareCredentials() để gỡ sạch Cookie và Token người dùng,
   * đảm bảo request
   * xuống service hoàn toàn "sạch sẽ".
   *
   * @param exchange Ngữ cảnh HTTP hiện tại.
   * @param chain    Chuỗi filter tiếp theo của Gateway.
   * @return Mono<Void> tín hiệu hoàn thành phản ứng (Reactive non-blocking).
   */
  // #region forwardWithoutContext
  public Mono<Void> forwardWithoutContext(ServerWebExchange exchange, GatewayFilterChain chain) {
    var request = prepareCredentials(exchange).build();
    return chain.filter(exchange.mutate().request(request).build());
  }

  /**
   * BƯỚC 3B: CHUYỂN TIẾP REQUEST KÈM NGỮ CẢNH ĐÃ KÝ SỐ (AUTHENTICATED ENDPOINTS)
   *
   * <p>
   * Đây là phương thức quan trọng nhất trong cơ chế bảo mật Gateway: - Chỉ được
   * gọi SAU KHI
   * Gateway đã xác thực thành công Access Token và kiểm tra session/grants. - Tạo
   * một chữ ký số
   * (HMAC signature) bọc dữ liệu danh tính thông qua signContext(). - Bơm
   * (Inject) các header tin
   * cậy vào request gửi xuống downstream microservice: +
   * GatewayContextCodec.HEADER
   * ("x-gateway-context"): Chuỗi context kèm chữ ký số cryptographic. +
   * SecurityHeaders.X_USER_ID
   * ("x-user-id"): UUID của User. + SecurityHeaders.X_TENANT_ID ("x-tenant-id"):
   * UUID của Tổ
   * chức/Doanh nghiệp đang active (nếu có).
   *
   * @param exchange Ngữ cảnh HTTP hiện tại.
   * @param chain    Chuỗi filter tiếp theo của Gateway.
   * @param identity Danh tính đã được Gateway xác thực (User ID, Tenant ID, Role,
   *                 Actor).
   * @return Mono<Void> kết quả chuyển tiếp request.
   */
  // #region forwardWithContext
  public Mono<Void> forwardWithContext(
      ServerWebExchange exchange, GatewayFilterChain chain, Identity identity) {
    // 1. Tạo chuỗi context đã ký số cho service đích
    String context = signContext(exchange, identity);

    // 2. Chuẩn bị request: xóa sạch token cũ, chỉ truyền context & headers danh
    // tính chính chủ
    var request = prepareCredentials(exchange)
        .headers(
            headers -> {
              // Header chứa context mã hóa kèm chữ ký số để service downstream xác minh
              headers.set(GatewayContextCodec.HEADER, context);
              // Cung cấp nhanh ID để downstream truy vấn thuận tiện mà không cần decode
              // context
              // phức tạp
              headers.set(SecurityHeaders.X_USER_ID, identity.userId().toString());
              if (identity.tenantId() != null) {
                headers.set(SecurityHeaders.X_TENANT_ID, identity.tenantId().toString());
              }
            })
        .build();

    // 3. Chuyển tiếp request mới xuống downstream service
    return chain.filter(exchange.mutate().request(request).build());
  }

  /**
   * CHUẨN BỊ CREDENTIAL VÀ CÔ LẬP TOKEN (CREDENTIAL STRIPPING & REFRESH
   * FORWARDING)
   *
   * <p>
   * Nguyên lý bảo mật Zero-Trust: 1. Access Token DỪNG LẠI TẠI GATEWAY: Gỡ bỏ
   * toàn bộ header
   * "Authorization" và "Cookie" gốc. Các service downstream (như Order, WMS,
   * Inventory) không bao
   * giờ nhìn thấy Access Token gốc của user. 2. Riêng Refresh Token: Chỉ duy nhất
   * các endpoint đặc
   * biệt của IAM (POST /api/v1/auth/refresh, /logout) mới được phép nhận cookie
   * refresh_token theo
   * đánh giá của GatewayRequestPolicy. Nếu hợp lệ, Gateway sẽ tái tạo một header
   * Cookie DUY NHẤT
   * chỉ chứa "refresh_token=...".
   */
  // #region prepareCredentials
  private ServerHttpRequest.Builder prepareCredentials(ServerWebExchange exchange) {
    var request = exchange.getRequest();

    // Kiểm tra xem route hiện tại có được phép forward Refresh Token xuống IAM
    // không
    String refresh = policy.canForwardRefreshToken(exchange)
        ? cookieValue(request, AuthCookies.REFRESH_TOKEN)
        : null;

    return request
        .mutate()
        .headers(
            headers -> {
              // Xóa toàn bộ Cookie và Authorization ban đầu để tránh rò rỉ credential
              headers.remove(HttpHeaders.COOKIE);
              headers.remove(HttpHeaders.AUTHORIZATION);

              // Chỉ đính kèm duy nhất cookie refresh_token nếu route đích thỏa mãn policy
              if (refresh != null) {
                headers.set(HttpHeaders.COOKIE, AuthCookies.REFRESH_TOKEN + "=" + refresh);
              }
            });
  }

  /**
   * KÝ SỐ NGỮ CẢNH (CRYPTOGRAPHIC CONTEXT SIGNING)
   *
   * <p>
   * Thuật toán chống Replay Attack & Tampering: Gateway tạo chữ ký số bọc các
   * tham số: 1.
   * Identity (actor, roleId): Ai đang gọi request. 2. HTTP Method: GET, POST,
   * PUT, DELETE... 3.
   * Target Path + Query: Ví dụ "/api/v1/orders/123?action=ship".
   *
   * <p>
   * Ý nghĩa: - Nếu kẻ tấn công đánh cắp được header `x-gateway-context` của
   * request xem hàng
   * (GET /products), họ KHÔNG THỂ tái sử dụng header đó để thực hiện request xóa
   * đơn (DELETE
   * /orders/1) vì chữ ký sẽ không khớp! - Downstream service chỉ cần dùng
   * public/shared key để
   * verify chữ ký là biết chắc request này đã được Gateway kiểm định an toàn
   * 100%.
   */
  // #region signContext
  private String signContext(ServerWebExchange exchange, Identity identity) {
    var request = exchange.getRequest();

    // Lấy đường dẫn đầy đủ bao gồm cả URI Path và Query Params
    String target = request.getURI().getRawPath();
    String query = request.getURI().getRawQuery();
    if (query != null) {
      target += "?" + query;
    }

    // Chọn đúng bộ ký số (Signer) của service đích và tiến hành ký
    return signers
        .forService(policy.targetService(exchange))
        .sign(identity.actor(), identity.roleId(), request.getMethod().name(), target);
  }

  /**
   *
   * ĐỌC GIÁ TRỊ TỪ COOKIE AN TOÀN
   *
   * <p>
   * Biện pháp phòng ngừa: Chỉ chấp nhận khi cookie tồn tại và có ĐÚNG 1 bản ghi
   * duy nhất. Tránh
   * kỹ thuật tấn công "HTTP Cookie Poisoning / Cookie Bombing" (kẻ xấu gửi nhiều
   * cookie cùng tên để
   * làm sai lệch kết quả đọc cookie).
   */
  // #region cookieValue
  private String cookieValue(ServerHttpRequest request, String name) {
    var cookies = request.getCookies().get(name);
    return cookies != null && cookies.size() == 1 ? cookies.getFirst().getValue() : null;
  }
}
