package com.vishop.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.CacheKeyBuilder;
import io.github.coreplatform.common.cache.redis.VersionedAuthorizationCache;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import io.github.coreplatform.common.security.gateway.GatewayIdentityHeaders;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Trình đọc snapshot phân quyền cho Gateway từ Redis và đồng bộ lại với IAM khi cần. Không lưu
 * cache cục bộ trong RAM; các request miss đồng thời cho cùng 1 key sẽ dùng chung một lần gọi IAM
 * có giới hạn tải.
 */
@Component
public class AuthorizationCacheReader {
  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper json;
  private final WebClient http;
  private final CacheKeyBuilder keys;
  private final long maxAge;
  // Giới hạn tối đa 32 luồng gọi đồng thời tới /internal/authorization để bảo vệ iam-service khỏi
  // quá tải
  private final java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(32);
  // Single-flight map: gộp nhiều request đang cùng chờ tải 1 key về đúng 1 HTTP call duy nhất
  private final ConcurrentHashMap<String, Mono<AuthorizationSnapshot>> loads = new ConcurrentHashMap<>();

  public AuthorizationCacheReader(
      ReactiveStringRedisTemplate redis,
      ObjectMapper json,
      @Value("${IAM_SERVICE_URL:http://localhost:8080}") String url,
      CacheKeyBuilder keys,
      @Value("${vishop.security.authorization.max-age-seconds:30}") long maxAge) {
    if (maxAge < 1 || maxAge > 300)
      throw new IllegalArgumentException("Authorization max age must be 1..300 seconds");
    this.redis = redis;
    this.json = json;
    this.http = WebClient.builder().baseUrl(url).build();
    this.keys = keys;
    this.maxAge = maxAge;
  }

  /**
   * Ưu tiên đọc từ Redis; nếu không có hoặc dữ liệu đã cũ quá {@code maxAge}, tự động tải lại từ
   * IAM.
   */
  //#region get
  public Mono<AuthorizationSnapshot> get(String key) {
    // Dùng Mono.defer để refresh(key) chỉ được tạo và chạy khi read(key) thực sự trả về empty
    return read(key).switchIfEmpty(Mono.defer(() -> refresh(key)));
  }

  /**
   * Tải trực tiếp snapshot mới nhất từ IAM (dùng khi cache miss, stale hoặc tokenVersion mới hơn
   * cache).
   */
  //#region refresh
  public Mono<AuthorizationSnapshot> refresh(String key) {
    return Mono.defer(
        () -> loads.computeIfAbsent(
            key,
            k -> Mono.defer(
                () -> {
                  // Fail-fast nếu đã đạt ngưỡng tối đa 32 kết nối đồng thời tới IAM
                  if (!permits.tryAcquire())
                    return Mono.error(
                        new IllegalStateException("Authorization loader at capacity"));
                  // Luôn nhả semaphore khi kết thúc (thành công, lỗi hoặc bị hủy)
                  return load(k).doFinally(signal -> permits.release());
                })
                // Không chờ phản hồi IAM quá 3 giây để tránh treo luồng Gateway
                .timeout(Duration.ofSeconds(3))
                // Xóa key khỏi map đang tải để các lần refresh sau có thể lấy dữ liệu mới
                .doFinally(signal -> loads.remove(k))
                // Chia sẻ kết quả của cùng một Mono cho tất cả caller đang chờ chung key
                .cache()));
  }

  //#region read
  private Mono<AuthorizationSnapshot> read(String key) {
    return redis
        .execute(
            // Lua script trả về nil nếu key quá maxAge (theo Redis TIME) để ép refresh
            new DefaultRedisScript<String>(VersionedAuthorizationCache.READ, String.class),
            List.of(keys.withPrefix(key)),
            List.of(Long.toString(maxAge)))
        .next()
        .map(
            value -> {
              var state = decode(value);
              // Đối chiếu lại key bên trong payload để tránh nhầm lẫn dữ liệu / cache corruption
              if (!key.equals(state.key()))
                throw new IllegalStateException("Cached authorization key mismatch");
              return state;
            });
  }

  //#region load
  private Mono<AuthorizationSnapshot> load(String key) {
    URI target = UriComponentsBuilder.fromPath("/internal/authorization")
        .queryParam("key", key)
        .build()
        .encode()
        .toUri();
    return http.get()
        .uri(target.toString())
        // Gắn header kỹ thuật (00000000-...) vì /internal/authorization chặn user thường
        .headers(headers -> headers.putAll(GatewayIdentityHeaders.technical().toHeaders()))
        .retrieve()
        .onStatus(
            s -> s.value() == 404,
            // 404 từ IAM nghĩa là không có trạng thái active (user/role bị khóa hoặc rời tenant)
            r -> Mono.error(
                new ForbiddenException(
                    key.startsWith("auth:member:")
                        ? "membership-inactive"
                        : "access-not-active",
                    "No active authorization state")))
        .bodyToMono(AuthorizationSnapshot.class)
        .switchIfEmpty(Mono.error(new IllegalStateException("Empty authorization projection")))
        .flatMap(
            state -> {
              // Từ chối ngay nếu phiên đăng nhập trong snapshot đã hết hạn
              if (state.expiresAt() != null && !state.expiresAt().isAfter(java.time.Instant.now()))
                return Mono.error(new UnauthorizedException("session-expired", "session-expired"));
              if (!key.equals(state.key()))
                return Mono.error(new IllegalStateException("Authorization key mismatch"));
              try {
                return redis
                    .execute(
                        // Lua script chỉ ghi đè khi revision mới >= Redis, cập nhật checkedAt & TTL
                        new DefaultRedisScript<Long>(VersionedAuthorizationCache.WRITE, Long.class),
                        List.of(keys.withPrefix(key)),
                        List.of(
                            state.version(),
                            json.writeValueAsString(state),
                            state.expiresAt() == null
                                ? ""
                                : Long.toString(state.expiresAt().getEpochSecond())))
                    // Đọc lại từ Redis để xác nhận cache hoạt động trước khi trả kết quả
                    .then(read(key))
                    .switchIfEmpty(
                        Mono.error(new IllegalStateException("Authorization cache unavailable")));
              } catch (Exception e) {
                return Mono.error(e);
              }
            });
  }

  //#region decode
  private AuthorizationSnapshot decode(String value) {
    try {
      return json.readValue(value, AuthorizationSnapshot.class);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid cached authorization", e);
    }
  }
}
