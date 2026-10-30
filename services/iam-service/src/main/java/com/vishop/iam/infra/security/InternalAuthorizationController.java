package com.vishop.iam.infra.security;

import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Infrastructure-only cache loader. Its signed caller has no delegated user privileges. */
@RestController
public class InternalAuthorizationController {
  private final AuthorizationProjection projection;

  public InternalAuthorizationController(AuthorizationProjection projection) {
    this.projection = projection;
  }

  @GetMapping("/internal/authorization")
  public ResponseEntity<AuthorizationSnapshot> get(@RequestParam String key) {
    if (!key.matches("auth:(user|tenant):[0-9a-f-]{36}|auth:role:[0-9a-f-]{36}:[0-9a-f-]{36}"))
      return ResponseEntity.badRequest().build();
    var value = projection.find(key);
    return value == null
        ? ResponseEntity.notFound().build()
        : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }
}
