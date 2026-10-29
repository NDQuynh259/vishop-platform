package io.github.coreplatform.common.security.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.domain.constants.SystemRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticates gateway context, independently of end-user JWT credentials. */
public final class GatewayContextCodec {
  public static final String HEADER = "X-Gateway-Context";
  private final ObjectMapper json;
  private final byte[] secret;
  private final String audience;
  private final Clock clock;

  public record Context(
      UUID userId,
      UUID tenantId,
      UUID roleId,
      String systemRole,
      String tenantRole,
      Set<String> permissions,
      long issuedAt,
      String requestId) {}

  public GatewayContextCodec(ObjectMapper json, String secret, String audience) {
    this(json, secret, audience, Clock.systemUTC());
  }

  public GatewayContextCodec(ObjectMapper json, String secret, String audience, Clock clock) {
    this.json = json;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    if (this.secret.length < 32)
      throw new IllegalArgumentException("Gateway context secret needs at least 32 bytes");
    this.audience = audience;
    this.clock = clock;
  }

  public String sign(GatewayPrincipal actor, UUID roleId, String method, String target) {
    try {
      var context =
          new Context(
              actor.userId(),
              actor.tenantId(),
              roleId,
              actor.systemRole(),
              actor.tenantRole(),
              actor.permissions(),
              clock.instant().getEpochSecond(),
              UUID.randomUUID().toString());
      String body =
          Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(context));
      return body
          + "."
          + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(method, target, body));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot sign gateway context", e);
    }
  }

  public GatewayPrincipal verify(String value, String method, String target) {
    try {
      if (value == null || value.length() > 32768) throw new IllegalArgumentException();
      String[] parts = value.split("\\.", -1);
      if (parts.length != 2
          || !MessageDigest.isEqual(
              mac(method, target, parts[0]), Base64.getUrlDecoder().decode(parts[1])))
        throw new IllegalArgumentException();
      var c = json.readValue(Base64.getUrlDecoder().decode(parts[0]), Context.class);
      long age = clock.instant().getEpochSecond() - c.issuedAt();
      if (age < -2
          || age > 15
          || c.userId() == null
          || !SystemRole.isValid(c.systemRole())
          || c.permissions() == null
          || c.permissions().stream().anyMatch(p -> p == null || p.isBlank())
          || (c.tenantId() == null
              ? c.tenantRole() != null || c.roleId() != null || !c.permissions().isEmpty()
              : c.roleId() == null || c.tenantRole() == null || c.tenantRole().isBlank()))
        throw new IllegalArgumentException();
      return new GatewayPrincipal(
          c.userId(), c.tenantId(), c.systemRole(), c.tenantRole(), Set.copyOf(c.permissions()));
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid gateway context", e);
    }
  }

  private byte[] mac(String method, String target, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    return mac.doFinal(
        ("v1\n" + audience + "\n" + method + "\n" + target + "\n" + body)
            .getBytes(StandardCharsets.UTF_8));
  }
}
