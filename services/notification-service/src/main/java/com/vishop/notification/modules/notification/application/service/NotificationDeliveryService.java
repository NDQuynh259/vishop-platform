package com.vishop.notification.modules.notification.application.service;

import com.vishop.notification.modules.notification.application.api.NotificationDeliveryApi;
import com.vishop.notification.modules.notification.domain.entity.NotificationDelivery;
import com.vishop.notification.modules.notification.domain.port.NotificationDeliveryRepository;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class NotificationDeliveryService implements NotificationDeliveryApi {
  private final NotificationDeliveryRepository repository;
  private final Clock clock;
  private final Supplier<UUID> ids;
  private final Set<NotificationChannel> supportedChannels;

  public NotificationDeliveryService(
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
    var jobs = new LinkedHashMap<Key, NotificationDelivery>();
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
            NotificationDelivery.pending(
                ids.get(),
                tenantId,
                notificationId,
                target.userId(),
                target.channel(),
                destination,
                now,
                target.settingId()));
    }
    if (!jobs.isEmpty()) repository.append(List.copyOf(jobs.values()));
    return jobs.values().stream().map(NotificationDelivery::id).toList();
  }
}
