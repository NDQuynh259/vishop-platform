package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import com.vishop.notification.modules.setting.application.service.NotificationAlertRuleService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateRuleCommandHandler implements CommandHandler<CreateRuleCommand, RuleView> {
  private final NotificationAlertRuleService service;

  @Override
  @Transactional
  public RuleView handle(CreateRuleCommand request) {
    return service.createRule(
        request.getTenantId(), request.getType(), request.isEnabled(), request.getChannels());
  }
}
