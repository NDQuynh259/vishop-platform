package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import com.vishop.notification.modules.setting.application.service.NotificationAlertRuleService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateRuleCommandHandler implements CommandHandler<UpdateRuleCommand, RuleView> {
  private final NotificationAlertRuleService service;

  @Override
  @Transactional
  public RuleView handle(UpdateRuleCommand request) {
    return service.updateRule(request.getTenantId(), request.getRuleId(), request.getChange());
  }
}
