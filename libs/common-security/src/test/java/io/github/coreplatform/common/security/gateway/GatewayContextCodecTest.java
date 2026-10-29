package io.github.coreplatform.common.security.gateway;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GatewayContextCodecTest {
  final String secret = "gateway-secret-with-at-least-thirty-two-bytes";
  final Instant now = Instant.parse("2026-10-29T00:00:00Z");

  GatewayContextCodec codec(String audience, Instant time) {
    return new GatewayContextCodec(
        new ObjectMapper(), secret, audience, Clock.fixed(time, ZoneOffset.UTC));
  }

  @Test
  void contextBindsIdentityPermissionsAudienceMethodAndQuery() {
    var actor =
        new GatewayPrincipal(
            UUID.randomUUID(), UUID.randomUUID(), "USER", "WAREHOUSE", Set.of("inventory.view"));
    var codec = codec("subscription-service", now);
    String value = codec.sign(actor, UUID.randomUUID(), "GET", "/api/v1/billing/orders?limit=1");
    assertThat(codec.verify(value, "GET", "/api/v1/billing/orders?limit=1")).isEqualTo(actor);
    assertThatThrownBy(() -> codec.verify(value, "PUT", "/api/v1/billing/orders?limit=1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> codec.verify(value, "GET", "/api/v1/billing/orders?limit=2"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> codec("iam-service", now).verify(value, "GET", "/api/v1/billing/orders?limit=1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                codec("subscription-service", now.plusSeconds(16))
                    .verify(value, "GET", "/api/v1/billing/orders?limit=1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> codec.verify("x" + value, "GET", "/api/v1/billing/orders?limit=1"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
