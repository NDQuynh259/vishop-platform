package com.vishop.notification.modules.inbox.domain.entity;

import com.vishop.notification.shared.contract.NotificationMessage.Severity;
import java.time.Instant;
import java.util.UUID;
import lombok.Value;

@Value
public class NotificationInboxFilter {
  String type;
  Severity severity;
  Boolean read;
  UUID shopId;
  Instant beforeCreatedAt;
  UUID beforeId;
  int limit;
}
