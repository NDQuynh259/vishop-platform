package com.vishop.notification.modules.delivery.application.command;

import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class ReserveRoutesCommand implements Command<Map<UUID, Set<NotificationChannel>>> {
  NotificationMessage message;
  Map<UUID, Set<NotificationChannel>> routes;

  public ReserveRoutesCommand(
      NotificationMessage message, Map<UUID, Set<NotificationChannel>> routes) {
    this.message = Objects.requireNonNull(message);
    var snapshot = new HashMap<UUID, Set<NotificationChannel>>();
    routes.forEach((user, channels) -> snapshot.put(user, Set.copyOf(channels)));
    this.routes = Map.copyOf(snapshot);
  }
}
