package io.github.coreplatform.common.security.gateway;

import static io.github.coreplatform.common.security.constants.SecurityHeaders.*;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.Getter;
import org.springframework.http.HttpHeaders;

/** Gateway supplies identifiers and verified system role; tenant grants are resolved locally. */
@Getter
public final class GatewayIdentityHeaders {
  private final UUID userId;
  private final UUID sessionId;
  private final UUID tenantId;
  private final UUID roleId;
  private final String systemRole;

  public static final UUID TECHNICAL_USER = new UUID(0, 0);
  private static final List<String> NAMES = List.of(X_USER_ID, X_SESSION_ID, X_TENANT_ID, X_ROLE_ID, X_SYSTEM_ROLE);

  public GatewayIdentityHeaders(
      UUID userId, UUID sessionId, UUID tenantId, UUID roleId, String systemRole) {
    if (userId == null
        || !SystemRole.isValid(systemRole)
        || (TECHNICAL_USER.equals(userId)
            ? sessionId != null || tenantId != null || roleId != null || !"USER".equals(systemRole)
            : sessionId == null || TECHNICAL_USER.equals(sessionId))
        || (tenantId == null
            ? roleId != null
            : TECHNICAL_USER.equals(tenantId) || roleId == null || TECHNICAL_USER.equals(roleId))) {
      throw new UnauthorizedException("Invalid gateway identity headers");
    }
    this.userId = userId;
    this.sessionId = sessionId;
    this.tenantId = tenantId;
    this.roleId = roleId;
    this.systemRole = systemRole;
  }

  public static GatewayIdentityHeaders read(Function<String, List<String>> headers) {
    if (NAMES.stream()
        .allMatch(name -> headers.apply(name) == null || headers.apply(name).isEmpty()))
      return null;
    try {
      return new GatewayIdentityHeaders(
          id(one(headers, X_USER_ID)),
          id(one(headers, X_SESSION_ID)),
          id(one(headers, X_TENANT_ID)),
          id(one(headers, X_ROLE_ID)),
          one(headers, X_SYSTEM_ROLE));
    } catch (IllegalArgumentException ex) {
      throw new UnauthorizedException("Invalid gateway identity headers");
    }
  }

  private static String one(Function<String, List<String>> headers, String name) {
    var values = headers.apply(name);
    if (values == null || values.isEmpty())
      return null;
    if (values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) {
      throw new UnauthorizedException("Ambiguous or empty gateway identity header");
    }
    return values.getFirst();
  }

  private static UUID id(String value) {
    if (value == null)
      return null;
    UUID id = UUID.fromString(value);
    if (!id.toString().equalsIgnoreCase(value))
      throw new IllegalArgumentException("Invalid UUID");
    return id;
  }

  public HttpHeaders toHeaders() {
    var headers = new HttpHeaders();
    headers.set(X_USER_ID, userId.toString());
    headers.set(X_SYSTEM_ROLE, systemRole);
    if (sessionId != null)
      headers.set(X_SESSION_ID, sessionId.toString());
    if (tenantId != null) {
      headers.set(X_TENANT_ID, tenantId.toString());
      headers.set(X_ROLE_ID, roleId.toString());
    }
    return headers;
  }

  public static GatewayIdentityHeaders technical() {
    return new GatewayIdentityHeaders(TECHNICAL_USER, null, null, null, "USER");
  }
}
