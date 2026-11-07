package com.vishop.notification.modules.inbox.application.dto;

import com.vishop.notification.shared.contract.NotificationMessage.Severity;
import java.time.Instant;
import java.util.UUID;
import lombok.Value;

@Value
public class NotificationView {
  UUID id;
  String type;
  String featureCode;
  String category;
  Severity severity;
  String title;
  String body;
  String actionUrl;
  UUID shopId;
  Instant occurredAt;
  Instant createdAt;
  Instant readAt;
}
