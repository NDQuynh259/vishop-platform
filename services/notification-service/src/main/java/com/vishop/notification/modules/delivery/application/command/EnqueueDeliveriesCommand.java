package com.vishop.notification.modules.delivery.application.command;

import com.vishop.notification.modules.delivery.application.api.NotificationDeliveryApi.Target;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.List;
import java.util.UUID;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class EnqueueDeliveriesCommand implements Command<List<UUID>> {
  UUID tenantId;
  UUID notificationId;
  List<Target> targets;

  public EnqueueDeliveriesCommand(UUID tenantId, UUID notificationId, List<Target> targets) {
    this.tenantId = tenantId;
    this.notificationId = notificationId;
    this.targets = List.copyOf(targets);
  }
}
