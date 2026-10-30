package com.vishop.iam.infra.authorization;

import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Chỉ gateway technical context được vào đây; filter chặn user context và request không có chữ ký.
 */
@RestController
@io.swagger.v3.oas.annotations.Hidden
public class InternalAuthorizationController {
  private final AuthorizationProjection projection;

  public InternalAuthorizationController(AuthorizationProjection projection) {
    this.projection = projection;
  }

  @GetMapping("/internal/authorization")
  public ResponseEntity<AuthorizationSnapshot> get(@RequestParam String key) {
    var state = projection.find(key);
    if (state == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(state);
  }
}
