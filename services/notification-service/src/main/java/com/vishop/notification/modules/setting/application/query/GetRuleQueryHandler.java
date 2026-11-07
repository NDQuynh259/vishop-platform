package com.vishop.notification.modules.setting.application.query;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import com.vishop.notification.modules.setting.application.service.NotificationAlertRuleService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetRuleQueryHandler implements QueryHandler<GetRuleQuery, RuleView> {
  private final NotificationAlertRuleService service;

  @Override
  @Transactional(readOnly = true)
  public RuleView handle(GetRuleQuery request) {
    return service.ruleById(request.getTenantId(), request.getRuleId());
  }
}
