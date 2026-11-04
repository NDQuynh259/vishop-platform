package com.vishop.notification.composition.dispatch;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Infrastructure adapter must implement a real DB transaction, consumed marker and outbox. Empty
 * means the key was already consumed. Failures must roll back all module writes.
 */
public interface DispatchJournal {
  record Key(UUID tenantId, String sourceEventId) {}

  <T> Optional<T> once(Key key, Supplier<T> operation);

  void notificationCreated(UUID tenantId, UUID notificationId, Set<UUID> inAppRecipients);
}
