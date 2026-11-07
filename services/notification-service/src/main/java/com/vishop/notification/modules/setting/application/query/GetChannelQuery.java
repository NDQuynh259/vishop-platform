package com.vishop.notification.modules.setting.application.query;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelView;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class GetChannelQuery implements Query<ChannelView> {
  UUID tenantId;
  UUID channelId;
}
