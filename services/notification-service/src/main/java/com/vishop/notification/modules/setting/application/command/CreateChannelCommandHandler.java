package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelView;
import com.vishop.notification.modules.setting.application.service.NotificationSettingService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateChannelCommandHandler
    implements CommandHandler<CreateChannelCommand, ChannelView> {
  private final NotificationSettingService service;

  @Override
  @Transactional
  public ChannelView handle(CreateChannelCommand command) {
    return service.createChannel(
        command.getTenantId(),
        command.getChannel(),
        command.getName(),
        command.isEnabled(),
        command.getConnectionConfig());
  }
}
