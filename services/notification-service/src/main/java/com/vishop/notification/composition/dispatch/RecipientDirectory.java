package com.vishop.notification.composition.dispatch;

import com.vishop.notification.modules.notification.application.api.NotificationDeliveryApi;
import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trusted adapter: active membership, permission/shop scope, type resolver and verified
 * destinations. Empty selectedRecipients means use a type-specific resolver, never all tenant
 * members. Implement from an authorized local projection; do not perform provider calls in the
 * transaction. Shared chat targets require a separate audience policy and collapse to one target
 * per chat.
 */
public interface RecipientDirectory {
  Set<UUID> eligible(NotificationMessage message, Set<UUID> selectedRecipients);

  List<NotificationDeliveryApi.Target> providerTargets(
      NotificationMessage message, Map<UUID, Set<NotificationChannel>> allowedRoutes);
}
