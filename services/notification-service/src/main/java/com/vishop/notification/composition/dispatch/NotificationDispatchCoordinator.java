package com.vishop.notification.composition.dispatch;

import com.vishop.notification.modules.configuration.application.api.ConfigurationApi;
import com.vishop.notification.modules.delivery.application.api.DeliveryApi;
import com.vishop.notification.modules.inbox.application.api.InboxApi;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Internal workflow only; inbound adapters must authenticate/trust the source event. */
public final class NotificationDispatchCoordinator {
  public record Result(
      Optional<UUID> notificationId, int inAppRecipientCount, List<UUID> deliveryIds) {
    public Result {
      Objects.requireNonNull(notificationId);
      deliveryIds = List.copyOf(deliveryIds);
    }
  }

  private final ConfigurationApi configuration;
  private final InboxApi inbox;
  private final DeliveryApi delivery;
  private final RecipientDirectory recipients;
  private final DispatchJournal journal;

  public NotificationDispatchCoordinator(
      ConfigurationApi configuration,
      InboxApi inbox,
      DeliveryApi delivery,
      RecipientDirectory recipients,
      DispatchJournal journal) {
    this.configuration = Objects.requireNonNull(configuration);
    this.inbox = Objects.requireNonNull(inbox);
    this.delivery = Objects.requireNonNull(delivery);
    this.recipients = Objects.requireNonNull(recipients);
    this.journal = Objects.requireNonNull(journal);
  }

  public Optional<Result> dispatch(NotificationMessage message) {
    Objects.requireNonNull(message);
    return journal.once(
        new DispatchJournal.Key(message.tenantId(), message.sourceEventId()),
        () -> dispatchOnce(message));
  }

  private Result dispatchOnce(NotificationMessage message) {
    var type =
        NotificationTypeCode.fromCode(message.type())
            .orElseThrow(() -> new IllegalArgumentException("Unknown notification type"));
    var policy =
        configuration.resolve(
            message.tenantId(),
            new ConfigurationApi.TypePolicy(
                type.code(),
                type.feature().code(),
                true,
                type.defaultEnabled(),
                type.defaultChannels()));
    if (policy.channels().isEmpty()) return skipped();
    var routes = new HashMap<UUID, Set<NotificationChannel>>();
    for (UUID user : recipients.eligible(message, policy.selectedRecipients())) {
      if (!policy.selectedRecipients().isEmpty() && !policy.selectedRecipients().contains(user))
        continue;
      var channels =
          configuration.restrictForUser(
              message.tenantId(), user, message.type(), policy.channels());
      if (!channels.isEmpty()) routes.put(user, channels);
    }
    var allowed = delivery.reserveAllowedRoutes(message, Map.copyOf(routes));
    if (allowed.isEmpty()) return skipped();
    var inApp =
        allowed.entrySet().stream()
            .filter(entry -> entry.getValue().contains(NotificationChannel.IN_APP))
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    var targets = recipients.providerTargets(message, allowed);
    if (inApp.isEmpty() && targets.isEmpty()) return skipped();
    UUID id = inbox.create(message, inApp);
    var jobs = delivery.enqueue(message.tenantId(), id, targets);
    if (!inApp.isEmpty()) journal.notificationCreated(message.tenantId(), id, inApp);
    return new Result(Optional.of(id), inApp.size(), jobs);
  }

  private static Result skipped() {
    return new Result(Optional.empty(), 0, List.of());
  }
}
