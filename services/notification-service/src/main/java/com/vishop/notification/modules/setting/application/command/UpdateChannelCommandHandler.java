package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelView;
import com.vishop.notification.modules.setting.application.service.NotificationSettingService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateChannelCommandHandler
    implements CommandHandler<UpdateChannelCommand, ChannelView> {
  private final NotificationSettingService service;

  @Override
  @Transactional
  public ChannelView handle(UpdateChannelCommand command) {
    return service.updateChannel(
        command.getTenantId(), command.getChannelId(), command.getChange());
  }
}
