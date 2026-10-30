package com.vishop.iam.modules.identity.application.command.auth;

import com.vishop.iam.modules.identity.application.api.IdentityApi.AuthorizationDeliveryPort;
import com.vishop.iam.modules.identity.application.port.AuthTokens;
import com.vishop.iam.modules.identity.domain.port.AuthSessionRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class LogoutCommandHandler implements CommandHandler<LogoutCommand, Void> {
  private final AuthTokens tokens;
  private final AuthSessionRepositoryPort sessions;
  private final AuthorizationDeliveryPort delivery;
  private final IamEventPublisherPort events;
  private final TransactionTemplate transaction;

  public LogoutCommandHandler(
      AuthTokens tokens,
      AuthSessionRepositoryPort sessions,
      AuthorizationDeliveryPort delivery,
      IamEventPublisherPort events,
      PlatformTransactionManager manager) {
    this.tokens = tokens;
    this.sessions = sessions;
    this.delivery = delivery;
    this.events = events;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Void handle(LogoutCommand command) {
    var identity = identity(command);
    if (identity == null) return null; // Không có credential hợp lệ: controller vẫn xóa cookie.
    var session =
        transaction.execute(
            status -> {
              var found =
                  sessions
                      .find(identity.sessionId())
                      .filter(s -> s.userId().equals(identity.userId()));
              if (found.isEmpty()) return null;
              if (sessions.revoke(identity.sessionId(), identity.userId()))
                events.append(
                    "UserLoggedOut",
                    "User",
                    identity.userId(),
                    null,
                    Map.of("userId", identity.userId(), "sessionId", identity.sessionId()));
              return found.get();
            });
    // DB đã commit trước khi publish. Redis lỗi: DB/outbox vẫn giữ REVOKED để retry.
    // Chỉ trả thành công khi Redis đã nhận trạng thái, tránh JWT cũ dùng lại ngay sau logout.
    if (session != null) delivery.deliverSession(session.id());
    return null;
  }

  private AuthTokens.Identity identity(LogoutCommand command) {
    if (command.actor() != null && command.actor().sessionId() != null)
      return new AuthTokens.Identity(
          command.actor().userId(), command.actor().tenantId(), command.actor().sessionId(), 0);
    try {
      if (command.refreshToken() != null)
        return tokens.verifyRefresh(command.refreshToken()).identity();
    } catch (RuntimeException invalid) {
      /* Có thể dùng access còn hạn nếu refresh không hợp lệ. */
    }
    try {
      if (command.accessToken() != null)
        return tokens.verifyAccess(command.accessToken()).identity();
    } catch (RuntimeException invalid) {
      /* Logout không biến token rác thành actor hay audit. */
    }
    return null;
  }
}
