package io.github.coreplatform.common.security.gateway;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Bản chụp trạng thái hoặc quyền từ IAM, dùng để lưu vào cache phân quyền của gateway.
 *
 * <p>Mỗi bản ghi chỉ đại diện cho một loại dữ liệu, được xác định bằng {@code key}:
 *
 * <ul>
 *   <li>User: dùng {@code status}, {@code systemRole}, {@code tokenVersion}.
 *   <li>Tenant: dùng {@code status}.
 *   <li>Role của tenant: dùng {@code status}, {@code roleId}, {@code roleKey}, {@code permissions}.
 *   <li>Session: dùng {@code status}, {@code userId}, {@code tokenVersion}, {@code expiresAt}; key
 *       có TTL theo hạn tuyệt đối của phiên, để logout riêng thiết bị.
 * </ul>
 *
 * <p>Các trường không áp dụng cho loại bản ghi đó có thể để {@code null}; permissions mặc định là
 * tập rỗng và tokenVersion không dùng có thể là 0. Đây là dữ liệu cache, không phải claims JWT hay
 * entity ánh xạ bảng DB.
 *
 * @param key khóa logic của bản ghi, chưa gồm prefix ứng dụng/môi trường; xem {@link #user(UUID)},
 *     {@link #tenant(UUID)} và {@link #role(UUID, UUID)}
 * @param version phiên bản tăng dần của dữ liệu cache tại cùng một key, giúp chặn bản cập nhật cũ
 *     ghi đè bản mới; biểu diễn bằng chuỗi số thập phân để tránh mất độ chính xác khi so sánh qua
 *     Redis Lua; không phải phiên bản token
 * @param status trạng thái của user, tenant hoặc role; gateway chỉ cho phép sử dụng khi ACTIVE
 * @param systemRole quyền hệ thống của user, ví dụ USER hoặc PLATFORM_ADMIN; độc lập với role
 *     tenant
 * @param roleId ID role thuộc tenant trong key; không phải ID membership hay ID user
 * @param roleKey mã role, ví dụ OWNER hoặc WAREHOUSE_MANAGER; không phải khóa Redis và không đủ để
 *     xác định quyền nếu thiếu tenantId/roleId
 * @param permissions tập mã quyền hiện tại của role; sửa quyền cập nhật cache này mà không cần phát
 *     hành lại JWT
 * @param tokenVersion phiên bản thu hồi token của user; gateway đối chiếu với tokenVersion trong
 *     JWT. Tăng giá trị này làm token phiên bản cũ không còn hợp lệ, khác với version của bản ghi
 *     cache
 * @param userId chủ sở hữu session; gateway so với subject JWT để không dùng phiên của user khác
 * @param expiresAt hạn tuyệt đối của session; không gia hạn khi refresh và không áp dụng cho key
 *     role/user/tenant
 */
public record AuthorizationSnapshot(
    String key,
    String version,
    String status,
    String systemRole,
    UUID roleId,
    String roleKey,
    Set<String> permissions,
    long tokenVersion,
    UUID userId,
    Instant expiresAt) {
  public AuthorizationSnapshot(
      String key,
      String version,
      String status,
      String systemRole,
      UUID roleId,
      String roleKey,
      Set<String> permissions,
      long tokenVersion) {
    this(key, version, status, systemRole, roleId, roleKey, permissions, tokenVersion, null, null);
  }

  public static String session(UUID id) {
    return "auth:session:" + id;
  }

  /** Chuẩn hóa permissions rỗng và sao chép để bên gọi không thể thay đổi tập quyền đã lưu. */
  public AuthorizationSnapshot {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  /** Khóa trạng thái user, systemRole và phiên bản thu hồi token. */
  public static String user(UUID id) {
    return "auth:user:" + id;
  }

  /** Khóa trạng thái tenant, dùng để chặn truy cập khi tenant không còn hoạt động. */
  public static String tenant(UUID id) {
    return "auth:tenant:" + id;
  }

  /**
   * Khóa quyền của một role trong tenant cụ thể. Luôn kèm tenantId để không dùng nhầm quyền giữa
   * các tenant có cùng tên role. Lớp cache sẽ bổ sung prefix, ví dụ {@code vishop:dev:}.
   */
  public static String role(UUID tenant, UUID role) {
    return "auth:role:" + tenant + ":" + role;
  }
}
