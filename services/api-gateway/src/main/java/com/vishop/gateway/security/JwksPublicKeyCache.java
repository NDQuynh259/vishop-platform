package com.vishop.gateway.security;

import com.nimbusds.jose.jwk.JWKSet;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Bounded public-key cache, with one shared HTTP load and cooldown for unknown kid requests. */
final class JwksPublicKeyCache {
  private final WebClient client;
  private final String uri;
  private final Duration ttl;
  private final Duration refreshInterval;
  private JWKSet keys;
  private Instant expiresAt = Instant.MIN;
  private Instant nextRefreshAt = Instant.MIN;
  private Mono<JWKSet> loading;

  JwksPublicKeyCache(WebClient client, String uri, Duration ttl, Duration refreshInterval) {
    if (ttl.isNegative() || ttl.isZero() || refreshInterval.isNegative() || refreshInterval.isZero()
        || refreshInterval.compareTo(ttl) > 0)
      throw new IllegalArgumentException("JWKS TTL must be positive and >= refresh interval");
    this.client = client;
    this.uri = uri;
    this.ttl = ttl;
    this.refreshInterval = refreshInterval;
  }

  Mono<JWKSet> get(String kid) {
    return Mono.defer(() -> lookup(kid));
  }

  private synchronized Mono<JWKSet> lookup(String kid) {
    Instant now = Instant.now();
    if (keys != null && now.isBefore(expiresAt)) {
      if (keys.getKeyByKeyId(kid) != null || now.isBefore(nextRefreshAt)) return Mono.just(keys);
    }
    if (loading != null) return loading;
    if (now.isBefore(nextRefreshAt)) return Mono.error(new Unavailable());
    nextRefreshAt = now.plus(refreshInterval);
    loading = client.get().uri(uri).retrieve().bodyToMono(String.class)
        .timeout(Duration.ofSeconds(3))
        .map(body -> {
          try { return JWKSet.parse(body); }
          catch (Exception invalid) { throw new Unavailable(invalid); }
        })
        .switchIfEmpty(Mono.error(new Unavailable()))
        .doOnNext(this::store)
        .onErrorMap(error -> error instanceof Unavailable ? error : new Unavailable(error))
        .doFinally(signal -> clearLoading())
        .cache();
    return loading;
  }

  private synchronized void store(JWKSet value) {
    keys = value;
    expiresAt = Instant.now().plus(ttl);
  }

  private synchronized void clearLoading() { loading = null; }

  static final class Unavailable extends IllegalStateException {
    Unavailable() { super("JWKS temporarily unavailable"); }
    Unavailable(Throwable cause) { super("JWKS temporarily unavailable", cause); }
  }
}
