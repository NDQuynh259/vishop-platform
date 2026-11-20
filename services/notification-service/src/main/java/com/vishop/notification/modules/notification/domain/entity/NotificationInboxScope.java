package com.vishop.notification.modules.notification.domain.entity;

import java.util.UUID;
import lombok.Value;

@Value
public class NotificationInboxScope {
  UUID tenantId;
  UUID userId;
}
