package io.github.coreplatform.common.security.authentication;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;

/** Service-owned conversion and authorization contract for already verified JWT claims. */
@FunctionalInterface
public interface JwtAuthenticationConverter {
  Authentication convert(Claims claims);
}
