package com.vishop.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.redis.VersionedAuthorizationCache;
import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/** No local permission cache. Concurrent misses for a key share one bounded IAM load. */
@Component
public class AuthorizationCacheReader {
  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper json;
  private final GatewayContextCodec iam;
  private final WebClient http;
  private final String prefix;
  private final long maxAge;
  private final java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(32);
  private final ConcurrentHashMap<String, Mono<AuthorizationSnapshot>> loads =
      new ConcurrentHashMap<>();

  public AuthorizationCacheReader(
      ReactiveStringRedisTemplate redis,
      ObjectMapper json,
      GatewayContextSigners signers,
      @Value("${IAM_SERVICE_URL:http://localhost:8080}") String url,
      @Value("${vishop.security.authorization.cache-prefix:vishop:}") String prefix,
      @Value("${vishop.security.authorization.max-age-seconds:30}") long maxAge) {
    if (maxAge < 1 || maxAge > 300)
      throw new IllegalArgumentException("Authorization max age must be 1..300 seconds");
    this.redis = redis;
    this.json = json;
    this.iam = signers.forService("iam-service");
    this.http = WebClient.builder().baseUrl(url).build();
    this.prefix = prefix;
    this.maxAge = maxAge;
  }

  public Mono<AuthorizationSnapshot> get(String key) {
    return read(key).switchIfEmpty(Mono.defer(() -> refresh(key)));
  }

  /** Revalidates against IAM when a freshly issued credential is ahead of the cache. */
  public Mono<AuthorizationSnapshot> refresh(String key) {
    return Mono.defer(
        () ->
            loads.computeIfAbsent(
                key,
                k ->
                    Mono.defer(
                            () -> {
                              if (!permits.tryAcquire())
                                return Mono.error(
                                    new IllegalStateException("Authorization loader at capacity"));
                              return load(k).doFinally(signal -> permits.release());
                            })
                        .timeout(Duration.ofSeconds(3))
                        .doFinally(signal -> loads.remove(k))
                        .cache()));
  }

  private Mono<AuthorizationSnapshot> read(String key) {
    return redis
        .execute(
            new DefaultRedisScript<String>(VersionedAuthorizationCache.READ, String.class),
            List.of(prefix + key),
            List.of(Long.toString(maxAge)))
        .next()
        .map(
            value -> {
              var state = decode(value);
              if (!key.equals(state.key()))
                throw new IllegalStateException("Cached authorization key mismatch");
              return state;
            });
  }

  private Mono<AuthorizationSnapshot> load(String key) {
    URI target =
        UriComponentsBuilder.fromPath("/internal/authorization")
            .queryParam("key", key)
            .build()
            .encode()
            .toUri();
    String requestTarget = target.getRawPath() + "?" + target.getRawQuery();
    var caller = new GatewayPrincipal(new UUID(0, 0), null, "USER", null, Set.of());
    return http.get()
        .uri(target.toString())
        .header(GatewayContextCodec.HEADER, iam.sign(caller, null, "GET", requestTarget))
        .retrieve()
        .onStatus(
            s -> s.value() == 404,
            r -> Mono.error(new AuthenticationFailure(403, "access-not-active")))
        .bodyToMono(AuthorizationSnapshot.class)
        .switchIfEmpty(Mono.error(new IllegalStateException("Empty authorization projection")))
        .flatMap(
            state -> {
              if (state.expiresAt() != null && !state.expiresAt().isAfter(java.time.Instant.now()))
                return Mono.error(new AuthenticationFailure(401, "session-expired"));
              if (!key.equals(state.key()))
                return Mono.error(new IllegalStateException("Authorization key mismatch"));
              try {
                return redis
                    .execute(
                        new DefaultRedisScript<Long>(VersionedAuthorizationCache.WRITE, Long.class),
                        List.of(prefix + key),
                        List.of(
                            state.version(),
                            json.writeValueAsString(state),
                            state.expiresAt() == null
                                ? ""
                                : Long.toString(state.expiresAt().getEpochSecond())))
                    .then(read(key))
                    .switchIfEmpty(
                        Mono.error(new IllegalStateException("Authorization cache unavailable")));
              } catch (Exception e) {
                return Mono.error(e);
              }
            });
  }

  private AuthorizationSnapshot decode(String value) {
    try {
      return json.readValue(value, AuthorizationSnapshot.class);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid cached authorization", e);
    }
  }
}
