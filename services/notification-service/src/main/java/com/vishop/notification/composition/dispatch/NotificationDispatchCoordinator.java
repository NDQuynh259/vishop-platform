package com.vishop.notification.composition.dispatch;

import com.vishop.notification.modules.notification.application.command.CreateNotificationCommand;
import com.vishop.notification.modules.notification.application.command.EnqueueDeliveriesCommand;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
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

  private final NotificationDispatchPolicy setting;
  private final CommandBus commands;
  private final RecipientDirectory recipients;
  private final DispatchJournal journal;

  public NotificationDispatchCoordinator(
      NotificationDispatchPolicy setting,
      CommandBus commands,
      RecipientDirectory recipients,
      DispatchJournal journal) {
    this.setting = Objects.requireNonNull(setting);
    this.commands = Objects.requireNonNull(commands);
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
        setting.resolve(
            message.tenantId(),
            new NotificationDispatchPolicy.TypePolicy(
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
          setting.restrictForUser(message.tenantId(), user, message.type(), policy.channels());
      if (!channels.isEmpty()) routes.put(user, channels);
    }
    var allowed = Map.copyOf(routes);
    if (allowed.isEmpty()) return skipped();
    var inApp =
        allowed.entrySet().stream()
            .filter(entry -> entry.getValue().contains(NotificationChannel.IN_APP))
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    var targets = recipients.providerTargets(message, allowed);
    if (inApp.isEmpty() && targets.isEmpty()) return skipped();
    UUID id = commands.dispatch(new CreateNotificationCommand(message, inApp));
    var jobs = commands.dispatch(new EnqueueDeliveriesCommand(message.tenantId(), id, targets));
    if (!inApp.isEmpty()) journal.notificationCreated(message.tenantId(), id, inApp);
    return new Result(Optional.of(id), inApp.size(), jobs);
  }

  private static Result skipped() {
    return new Result(Optional.empty(), 0, List.of());
  }
}
