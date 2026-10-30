package com.vishop.iam.modules.identity.application.service;

import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantAccessPort;
import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantGrant;
import com.vishop.iam.modules.identity.application.dto.AuthResult;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.application.port.AuthTokens;
import com.vishop.iam.modules.identity.domain.entity.AuthSession;
import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.AuthSessionRepositoryPort;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Một nơi kiểm tra trạng thái hiện tại, dùng chung cho refresh và request từ gateway. */
@Service
public class SessionAuthenticationService {
  private final UserRepositoryPort users;
  private final AuthSessionRepositoryPort sessions;
  private final TenantAccessPort tenants;
  private final AuthTokens tokens;

  public SessionAuthenticationService(
      UserRepositoryPort users,
      AuthSessionRepositoryPort sessions,
      TenantAccessPort tenants,
      AuthTokens tokens) {
    this.users = users;
    this.sessions = sessions;
    this.tenants = tenants;
    this.tokens = tokens;
  }

  public record Current(User user, AuthSession session) {}

  public Current requireCurrent(AuthTokens.Identity identity) {
    User user =
        users
            .user(identity.userId())
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
    user.requireActive();
    AuthSession session =
        sessions
            .find(identity.sessionId())
            .orElseThrow(() -> new UnauthorizedException("Unknown login session"));
    // tokenVersion thu hồi tất cả thiết bị; revokedAt thu hồi đúng một thiết bị.
    if (!session.userId().equals(user.id())
        || !session.active(Instant.now())
        || session.tokenVersion() != identity.tokenVersion()
        || user.tokenVersion() != identity.tokenVersion())
      throw new UnauthorizedException("Login session has expired or been revoked");
    return new Current(user, session);
  }

  public TenantGrant grant(UUID userId, UUID tenantId) {
    if (tenantId == null) return null;
    return tenants
        .activeGrant(userId, tenantId)
        .orElseThrow(() -> new ForbiddenException("No active membership in the selected tenant"));
  }

  public UUID selectAtLogin(User user, UUID requested) {
    if (requested != null) {
      grant(user.id(), requested);
      return requested;
    }
    if (user.lastTenantId() != null
        && tenants.activeGrant(user.id(), user.lastTenantId()).isPresent())
      return user.lastTenantId();
    var available = tenants.activeTenantIds(user.id());
    // Nhiều tenant mà chưa chọn: cấp identity token, client đọc /tenants rồi chọn qua refresh.
    return available.size() == 1 ? available.getFirst() : null;
  }

  public AuthResult issue(User user, AuthSession session, UUID tenantId, String existingRefresh) {
    var grant = grant(user.id(), tenantId);
    var actor =
        new AuthenticatedUser(
            user.id(),
            tenantId,
            user.systemRole().name(),
            grant == null ? null : grant.roleKey(),
            grant == null ? java.util.Set.of() : grant.permissions(),
            session.id());
    return new AuthResult(
        UserViewMapper.from(user),
        actor,
        grant == null ? null : grant.roleId(),
        tokens.issue(user, tenantId, grant, session, existingRefresh));
  }

  /** Gateway đã kiểm tra JWT/cache; IAM vẫn đọc DB để chặn ngay trạng thái tài khoản/phiên cũ. */
  public AuthenticatedUser resolveContext(AuthenticatedUser context, UUID roleId) {
    if (context.sessionId() == null) throw new UnauthorizedException("Session id is required");
    var session =
        sessions
            .find(context.sessionId())
            .orElseThrow(() -> new UnauthorizedException("Unknown session"));
    var current =
        requireCurrent(
            new AuthTokens.Identity(
                context.userId(), context.tenantId(), context.sessionId(), session.tokenVersion()));
    var grant = grant(context.userId(), context.tenantId());
    if (!current.user().systemRole().name().equals(context.systemRole())
        || (grant == null
            ? roleId != null || context.tenantRole() != null
            : !grant.roleId().equals(roleId) || !grant.roleKey().equals(context.tenantRole())))
      throw new UnauthorizedException("Security context has changed");
    return new AuthenticatedUser(
        current.user().id(),
        context.tenantId(),
        current.user().systemRole().name(),
        grant == null ? null : grant.roleKey(),
        grant == null ? java.util.Set.of() : grant.permissions(),
        session.id());
  }

  public AuthenticatedUser verifyAccessToken(String credential) {
    AuthTokens.Access claims;
    try {
      claims = tokens.verifyAccess(credential);
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid access token");
    }
    var identity = claims.identity();
    requireCurrent(identity);
    return resolveContext(
        new AuthenticatedUser(
            identity.userId(),
            identity.tenantId(),
            claims.systemRole().name(),
            claims.tenantRole(),
            java.util.Set.of(),
            identity.sessionId()),
        claims.tenantRoleId());
  }
}
