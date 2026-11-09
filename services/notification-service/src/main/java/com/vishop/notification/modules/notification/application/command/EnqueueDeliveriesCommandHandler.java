package com.vishop.notification.modules.notification.application.command;

import com.vishop.notification.modules.notification.application.api.NotificationDeliveryApi;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EnqueueDeliveriesCommandHandler
    implements CommandHandler<EnqueueDeliveriesCommand, List<UUID>> {
  private final NotificationDeliveryApi application;

  @Override
  @Transactional
  public List<UUID> handle(EnqueueDeliveriesCommand request) {
    return application.enqueue(
        request.getTenantId(), request.getNotificationId(), request.getTargets());
  }
}
