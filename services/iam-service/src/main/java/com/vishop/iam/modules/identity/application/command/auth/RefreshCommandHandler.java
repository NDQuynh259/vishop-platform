package com.vishop.iam.modules.identity.application.command.auth;

import com.vishop.iam.modules.identity.application.dto.AuthResult;
import com.vishop.iam.modules.identity.application.port.AuthTokens;
import com.vishop.iam.modules.identity.application.service.SessionAuthenticationService;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshCommandHandler implements CommandHandler<RefreshCommand, AuthResult> {
  private final AuthTokens tokens;
  private final UserRepositoryPort users;
  private final SessionAuthenticationService authentication;
  private final IamEventPublisherPort events;

  public RefreshCommandHandler(
      AuthTokens tokens,
      UserRepositoryPort users,
      SessionAuthenticationService authentication,
      IamEventPublisherPort events) {
    this.tokens = tokens;
    this.users = users;
    this.authentication = authentication;
    this.events = events;
  }

  @Transactional
  public AuthResult handle(RefreshCommand command) {
    AuthTokens.Refresh refresh;
    try {
      refresh = tokens.verifyRefresh(command.refreshToken());
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid refresh token");
    }
    users
        .lockUser(refresh.identity().userId())
        .orElseThrow(() -> new UnauthorizedException("Unknown account"));
    // Signature đúng chưa đủ: session phải còn sống và tokenVersion phải khớp DB.
    var current = authentication.requireCurrent(refresh.identity());
    var tenant = command.tenantId() == null ? refresh.identity().tenantId() : command.tenantId();
    boolean switched = !Objects.equals(tenant, refresh.identity().tenantId());
    if (switched) {
      authentication.grant(current.user().id(), tenant);
      users.rememberTenant(current.user().id(), tenant);
      events.append(
          "UserTenantSelected",
          "User",
          current.user().id(),
          tenant,
          Map.of(
              "userId",
              current.user().id(),
              "tenantId",
              tenant,
              "sessionId",
              current.session().id()));
    }
    // Refresh thường chỉ cấp access mới. Khi chọn tenant khác, refresh mới vẫn giữ deadline cũ.
    return authentication.issue(
        current.user(), current.session(), tenant, switched ? null : command.refreshToken());
  }
}
