package com.vishop.iam.modules.identity.application.command.switchtenant;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.service.TokenService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SwitchTenantCommandHandler
    implements CommandHandler<SwitchTenantCommand, AuthResponse> {
  private final TokenService tokens;

  public SwitchTenantCommandHandler(TokenService tokens) {
    this.tokens = tokens;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public AuthResponse handle(SwitchTenantCommand command) {
    return tokens.switchTenant(command.actor(), command.tenant(), command.refreshToken());
  }
}
