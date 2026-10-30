package com.vishop.iam.modules.identity.application.port;

import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantGrant;
import com.vishop.iam.modules.identity.domain.entity.AuthSession;
import com.vishop.iam.modules.identity.domain.entity.User;
import io.github.coreplatform.common.domain.constants.SystemRole;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Hợp đồng JWT của identity; adapter dùng common, không đưa thư viện JWT vào application. */
public interface AuthTokens {
  record Identity(UUID userId, UUID tenantId, UUID sessionId, long tokenVersion) {}

  record Access(Identity identity, SystemRole systemRole, UUID tenantRoleId, String tenantRole) {}

  record Refresh(Identity identity, Instant expiresAt) {}

  record Issued(
      String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {
    @Override
    public String toString() {
      return "Issued[credentials=REDACTED]";
    }
  }

  Duration refreshLifetime();

  Issued issue(
      User user,
      UUID tenantId,
      TenantGrant grant,
      AuthSession session,
      String existingRefreshToken);

  /**
   * Chỉ verify chữ ký/claims. SessionAuthenticationService mới thực hiện kiểm tra trạng thái DB.
   */
  Access verifyAccess(String token);

  Refresh verifyRefresh(String token);
}
