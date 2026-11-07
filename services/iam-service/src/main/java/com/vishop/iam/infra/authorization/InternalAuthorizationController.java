package com.vishop.iam.infra.authorization;

import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Internal loader for gateway-only ingress; technical identity cannot access user endpoints. */
@RestController
@io.swagger.v3.oas.annotations.Hidden
public class InternalAuthorizationController {
  private final AuthorizationProjection projection;

  public InternalAuthorizationController(AuthorizationProjection projection) {
    this.projection = projection;
  }

  @GetMapping("/internal/authorization")
  @PreAuthorize(
      "isAuthenticated() and principal.userId =="
          + " T(io.github.coreplatform.common.security.gateway.GatewayIdentityHeaders).TECHNICAL_USER")
  public ResponseEntity<AuthorizationSnapshot> get(@RequestParam String key) {
    var state = projection.find(key);
    if (state == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(state);
  }
}
