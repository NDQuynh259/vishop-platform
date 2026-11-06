package com.vishop.notification.modules.setting.domain.port;

import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.UUID;

public interface NotificationSettingManagementRepository {
  Connection connection(UUID tenant, NotificationChannel channel);

  Connection saveConnection(UUID tenant, Connection connection, long expectedVersion);
}
