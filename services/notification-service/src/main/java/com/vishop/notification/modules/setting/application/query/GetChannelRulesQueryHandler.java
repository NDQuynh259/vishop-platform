package com.vishop.notification.modules.setting.application.query;

import com.vishop.notification.modules.setting.application.service.NotificationSettingService;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelRulesView;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetChannelRulesQueryHandler
    implements QueryHandler<GetChannelRulesQuery, ChannelRulesView> {
  private final NotificationSettingService service;

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ChannelRulesView handle(GetChannelRulesQuery request) {
    return service.channelRules(request.tenantId(), request.settingId());
  }
}
