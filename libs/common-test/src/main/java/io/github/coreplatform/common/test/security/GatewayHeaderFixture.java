package io.github.coreplatform.common.test.security;

import io.github.coreplatform.common.security.gateway.GatewayIdentityHeaders;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import java.util.UUID;
import org.springframework.http.HttpHeaders;

/** Models the gateway's trusted ingress in HTTP application tests. */
public final class GatewayHeaderFixture {
  private GatewayHeaderFixture() {}

  public static HttpHeaders from(GatewayPrincipal actor, UUID roleId) {
    return new GatewayIdentityHeaders(
            actor.userId(), actor.sessionId(), actor.tenantId(), roleId, actor.systemRole())
        .toHeaders();
  }
}
