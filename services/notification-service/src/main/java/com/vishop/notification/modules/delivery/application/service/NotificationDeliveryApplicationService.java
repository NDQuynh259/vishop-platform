package com.vishop.notification.modules.delivery.application.service;

import com.vishop.notification.modules.delivery.application.api.NotificationDeliveryApi;
import com.vishop.notification.modules.delivery.domain.entity.NotificationDeliveryJob;
import com.vishop.notification.modules.delivery.domain.port.NotificationDeliveryRepository;
import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class NotificationDeliveryApplicationService implements NotificationDeliveryApi {
  private final NotificationDeliveryRepository repository;
  private final Clock clock;
  private final Supplier<UUID> ids;
  private final Set<NotificationChannel> supportedChannels;

  public NotificationDeliveryApplicationService(
      NotificationDeliveryRepository repository,
      Clock clock,
      Supplier<UUID> ids,
      Set<NotificationChannel> supportedChannels) {
    this.repository = Objects.requireNonNull(repository);
    this.clock = Objects.requireNonNull(clock);
    this.ids = Objects.requireNonNull(ids);
    this.supportedChannels = Set.copyOf(supportedChannels);
    if (this.supportedChannels.contains(NotificationChannel.IN_APP))
      throw new IllegalArgumentException("In-app is owned by inbox");
  }

  @Override
  public List<UUID> enqueue(UUID tenantId, UUID notificationId, List<Target> targets) {
    record Key(NotificationChannel channel, String destination) {}
    var jobs = new LinkedHashMap<Key, NotificationDeliveryJob>();
    var now = clock.instant();
    for (var target : List.copyOf(targets)) {
      if (!supportedChannels.contains(target.channel()))
        throw new IllegalArgumentException("Channel not supported by persistence/provider");
      if (target.destination() == null || target.destination().isBlank())
        throw new IllegalArgumentException("Destination required");
      String destination = target.destination().trim();
      String identity =
          target.channel() == NotificationChannel.EMAIL
              ? destination.toLowerCase(Locale.ROOT)
              : destination;
      var key = new Key(target.channel(), identity);
      if (!jobs.containsKey(key))
        jobs.put(
            key,
            NotificationDeliveryJob.pending(
                ids.get(),
                tenantId,
                notificationId,
                target.userId(),
                target.channel(),
                destination,
                now));
    }
    if (!jobs.isEmpty()) repository.append(List.copyOf(jobs.values()));
    return jobs.values().stream().map(NotificationDeliveryJob::id).toList();
  }

  @Override
  public Map<UUID, Set<NotificationChannel>> reserveAllowedRoutes(
      NotificationMessage message, Map<UUID, Set<NotificationChannel>> routes) {
    var input = new HashMap<UUID, Set<NotificationChannel>>();
    routes.forEach(
        (user, channels) -> input.put(Objects.requireNonNull(user), Set.copyOf(channels)));
    var reserved = repository.reserveAllowedRoutes(message, Map.copyOf(input), clock.instant());
    var result = new HashMap<UUID, Set<NotificationChannel>>();
    reserved.forEach(
        (user, channels) -> {
          var snapshot = Set.copyOf(channels);
          if (!input.containsKey(user) || !input.get(user).containsAll(snapshot))
            throw new IllegalStateException("Cooldown adapter expanded recipient policy");
          if (!snapshot.isEmpty()) result.put(user, snapshot);
        });
    return Map.copyOf(result);
  }
}
