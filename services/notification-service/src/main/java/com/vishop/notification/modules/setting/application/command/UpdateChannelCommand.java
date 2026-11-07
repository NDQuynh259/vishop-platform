package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelUpdate;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelView;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class UpdateChannelCommand implements Command<ChannelView> {
  UUID tenantId;
  UUID channelId;
  ChannelUpdate change;
}
