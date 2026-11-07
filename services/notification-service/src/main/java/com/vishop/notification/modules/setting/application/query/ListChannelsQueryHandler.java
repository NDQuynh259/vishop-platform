package com.vishop.notification.modules.setting.application.query;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelView;
import com.vishop.notification.modules.setting.application.service.NotificationSettingService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListChannelsQueryHandler
    implements QueryHandler<ListChannelsQuery, List<ChannelView>> {
  private final NotificationSettingService service;

  @Override
  @Transactional(readOnly = true)
  public List<ChannelView> handle(ListChannelsQuery request) {
    return service.channels(request.getTenantId());
  }
}
