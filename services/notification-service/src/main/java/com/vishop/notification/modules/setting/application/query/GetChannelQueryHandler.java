package com.vishop.notification.modules.setting.application.query;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelView;
import com.vishop.notification.modules.setting.application.service.NotificationSettingService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetChannelQueryHandler implements QueryHandler<GetChannelQuery, ChannelView> {
  private final NotificationSettingService service;

  @Override
  @Transactional(readOnly = true)
  public ChannelView handle(GetChannelQuery request) {
    return service.channelById(request.getTenantId(), request.getChannelId());
  }
}
