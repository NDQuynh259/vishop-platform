package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.service.NotificationSettingService;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelRulesView;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReplaceChannelRulesCommandHandler
    implements CommandHandler<ReplaceChannelRulesCommand, ChannelRulesView> {
  private final NotificationSettingService service;

  @Override
  @Transactional
  public ChannelRulesView handle(ReplaceChannelRulesCommand request) {
    return service.replaceChannelRules(
        request.tenantId(), request.settingId(), request.version(), request.ruleIds());
  }
}
