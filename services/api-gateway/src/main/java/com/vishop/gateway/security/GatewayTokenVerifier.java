package com.vishop.gateway.security;

import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

/** Cryptographic verification only; the caller validates IAM access claims and user version. */
@FunctionalInterface
public interface GatewayTokenVerifier {
  Mono<Claims> verify(String credential);
}
