package com.vishop.iam.modules.identity.presentation.rest;

import com.vishop.iam.modules.identity.application.dto.ResolvedIdentity;
import com.vishop.iam.modules.identity.application.query.IntrospectQuery;
import com.vishop.iam.modules.identity.presentation.rest.dto.IntrospectRequest;
import com.vishop.iam.shared.infrastructure.security.OpaqueTokens;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalAuthController {
  private final CommandBus commands;
  private final QueryBus queries;
  private final byte[] secretHash;

  public InternalAuthController(
      CommandBus commands,
      QueryBus queries,
      @Value("${vishop.security.internal-secret:${IAM_INTERNAL_SECRET:}}") String secret) {
    this.commands = commands;
    this.queries = queries;
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("IAM_INTERNAL_SECRET needs at least 32 bytes");
    secretHash = OpaqueTokens.hash(secret).getBytes(StandardCharsets.US_ASCII);
  }

  @PostMapping("/internal/auth/introspect")
  public ResponseEntity<ResolvedIdentity> introspect(
      @RequestHeader(name = "X-Iam-Internal-Key", defaultValue = "") String key,
      @Valid @RequestBody IntrospectRequest request) {
    if (!MessageDigest.isEqual(
        secretHash, OpaqueTokens.hash(key).getBytes(StandardCharsets.US_ASCII)))
      throw new UnauthorizedException("Invalid internal credentials");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            queries.dispatch(
                new IntrospectQuery(request.credential(), request.type().equals("BEARER"))));
  }
}
