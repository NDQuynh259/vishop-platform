package com.vishop.notification.modules.notification.domain.entity;

import java.time.Instant;
import lombok.Value;

@Value
public class NotificationInboxEntry {
  Notification notification;
  Instant readAt;
}
