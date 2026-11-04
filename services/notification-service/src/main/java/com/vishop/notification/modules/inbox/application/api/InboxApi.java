package com.vishop.notification.modules.inbox.application.api;

import com.vishop.notification.shared.contract.NotificationMessage;
import java.util.Set;
import java.util.UUID;

public interface InboxApi {
  UUID create(NotificationMessage message, Set<UUID> inAppRecipients);
}
