package com.vishop.iam.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.redis.VersionedAuthorizationCache;
import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable coalescing outbox: row locks keep relay replicas and concurrent mutations ordered. */
@Service
public class AuthorizationProjection {
  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationProjection.class);
  private final EntityManager db;
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final String prefix;

  public AuthorizationProjection(
      EntityManager db,
      StringRedisTemplate redis,
      ObjectMapper json,
      @Value("${vishop.security.authorization.cache-prefix:vishop:}") String prefix) {
    this.db = db;
    this.redis = redis;
    this.json = json;
    this.prefix = prefix;
  }

  @Transactional(readOnly = true)
  public AuthorizationSnapshot find(String key) {
    java.util.List<?> rows =
        db.createNativeQuery("SELECT payload::text FROM authorization_outbox WHERE state_key=:key")
            .setParameter("key", key)
            .getResultList();
    var values = rows.stream().map(value -> decode((String) value)).toList();
    return values.isEmpty() ? null : values.getFirst();
  }

  @Scheduled(
      initialDelayString = "${vishop.security.authorization.initial-delay-ms:1000}",
      fixedDelayString = "${vishop.security.authorization.publish-delay-ms:500}")
  @Transactional
  public void publish() {
    java.util.List<?> rows =
        db.createNativeQuery(
                "SELECT payload::text FROM authorization_outbox WHERE pending ORDER BY updated_at LIMIT 100 FOR UPDATE SKIP LOCKED")
            .getResultList();
    var pending = rows.stream().map(value -> decode((String) value)).toList();
    for (var state : pending) {
      try {
        redis.execute(
            new DefaultRedisScript<Long>(VersionedAuthorizationCache.WRITE, Long.class),
            List.of(prefix + state.key()),
            state.version(),
            json.writeValueAsString(state));
        db.createNativeQuery("UPDATE authorization_outbox SET pending=false WHERE state_key=:key")
            .setParameter("key", state.key())
            .executeUpdate();
      } catch (Exception failure) {
        LOG.warn("Authorization projection delivery failed; pending records will retry", failure);
        break;
      }
    }
  }

  private AuthorizationSnapshot decode(String value) {
    try {
      return json.readValue(value, AuthorizationSnapshot.class);
    } catch (Exception failure) {
      throw new IllegalStateException("Invalid authorization projection", failure);
    }
  }
}
