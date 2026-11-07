package com.vishop.notification.modules.setting.application.query;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import com.vishop.notification.modules.setting.application.service.NotificationAlertRuleService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListRulesQueryHandler implements QueryHandler<ListRulesQuery, List<RuleView>> {
  private final NotificationAlertRuleService service;

  @Override
  @Transactional(readOnly = true)
  public List<RuleView> handle(ListRulesQuery request) {
    return service.rules(request.getTenantId());
  }
}
