package com.vishop.notification.modules.setting.domain.port;

import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.List;
import java.util.UUID;

public interface NotificationSettingManagementRepository {
  Connection createConnection(UUID tenant, Connection connection);

  Connection connection(UUID tenant, NotificationChannel channel);

  List<Connection> connections(UUID tenant, NotificationChannel channel);

  Connection connectionById(UUID tenant, UUID channelId);

  Connection saveConnectionById(
      UUID tenant, UUID channelId, Connection connection, long expectedVersion);

  Connection saveConnection(UUID tenant, Connection connection, long expectedVersion);
}
