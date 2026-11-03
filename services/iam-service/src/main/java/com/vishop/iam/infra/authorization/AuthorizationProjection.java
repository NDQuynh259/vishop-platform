package com.vishop.iam.infra.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.shared.application.port.AuthorizationDeliveryPort;
import io.github.coreplatform.common.cache.AuthorizationCacheKeys;
import io.github.coreplatform.common.cache.CacheKeyBuilder;
import io.github.coreplatform.common.cache.redis.VersionedAuthorizationCache;
import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** DB là nguồn sự thật. Redis chỉ nhận snapshot đã commit, theo revision tăng dần từng key. */
@Service
public class AuthorizationProjection implements AuthorizationDeliveryPort {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AuthorizationProjection.class);
  private final EntityManager db;
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final CacheKeyBuilder keys;

  public AuthorizationProjection(
      EntityManager db, StringRedisTemplate redis, ObjectMapper json, CacheKeyBuilder keys) {
    this.db = db;
    this.redis = redis;
    this.json = json;
    this.keys = keys;
  }

  @Transactional(readOnly = true)
  public AuthorizationSnapshot find(String key) {
    var rows =
        db.createNativeQuery("SELECT payload::text FROM authorization_outbox WHERE state_key=:key")
            .setParameter("key", key)
            .getResultList();
    return rows.isEmpty() ? null : decode((String) rows.getFirst());
  }

  @Scheduled(
      initialDelayString = "${vishop.security.authorization.initial-delay-ms:1000}",
      fixedDelayString = "${vishop.security.authorization.publish-delay-ms:500}")
  @Transactional
  public void publish() {
    // Khóa row qua cả lần ghi Redis: replica khác và mutation cùng key không thể đảo thứ tự.
    List<?> rows =
        db.createNativeQuery(
                """
        SELECT payload::text FROM authorization_outbox WHERE pending
        ORDER BY updated_at LIMIT 100 FOR UPDATE SKIP LOCKED
        """)
            .getResultList();
    for (Object row : rows) {
      try {
        write(decode((String) row));
      } catch (Exception unavailable) {
        LOG.warn("Authorization cache delivery failed; durable pending rows will retry");
        break;
      }
    }
  }

  @Override
  @Transactional
  public void deliverSession(UUID sessionId) {
    deliver(AuthorizationCacheKeys.session(sessionId));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deliverMembership(UUID tenantId, UUID userId) {
    deliver(AuthorizationCacheKeys.member(tenantId, userId));
  }

  private void deliver(String key) {
    try {
      var rows =
          db.createNativeQuery(
                  "SELECT payload::text FROM authorization_outbox WHERE state_key=:key FOR UPDATE")
              .setParameter("key", key)
              .getResultList();
      if (rows.isEmpty()) throw new IllegalStateException("Missing authorization projection");
      write(decode((String) rows.getFirst()));
    } catch (RuntimeException failure) {
      throw new AuthorizationDeliveryPort.Unavailable(failure);
    }
  }

  private void write(AuthorizationSnapshot state) {
    try {
      // Session có TTL theo deadline refresh. User/tenant/role giữ revision watermark không TTL.
      Long accepted =
          redis.execute(
              new DefaultRedisScript<Long>(VersionedAuthorizationCache.WRITE, Long.class),
              List.of(keys.withPrefix(state.key())),
              state.version(),
              json.writeValueAsString(state),
              state.expiresAt() == null ? "" : Long.toString(state.expiresAt().getEpochSecond()));
      if (!Long.valueOf(1).equals(accepted))
        throw new IllegalStateException("No Redis acknowledgement");
      db.createNativeQuery("UPDATE authorization_outbox SET pending=false WHERE state_key=:key")
          .setParameter("key", state.key())
          .executeUpdate();
    } catch (java.io.IOException invalid) {
      throw new IllegalStateException("Invalid authorization payload", invalid);
    }
  }

  private AuthorizationSnapshot decode(String payload) {
    try {
      return json.readValue(payload, AuthorizationSnapshot.class);
    } catch (Exception invalid) {
      throw new IllegalStateException("Invalid authorization projection", invalid);
    }
  }
}
