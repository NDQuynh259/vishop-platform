package com.vishop.notification.modules.delivery.application.command;

import com.vishop.notification.modules.delivery.application.api.NotificationDeliveryApi;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReserveRoutesCommandHandler
    implements CommandHandler<ReserveRoutesCommand, Map<UUID, Set<NotificationChannel>>> {
  private final NotificationDeliveryApi application;

  @Override
  @Transactional
  public Map<UUID, Set<NotificationChannel>> handle(ReserveRoutesCommand request) {
    return application.reserveAllowedRoutes(request.getMessage(), request.getRoutes());
  }
}
