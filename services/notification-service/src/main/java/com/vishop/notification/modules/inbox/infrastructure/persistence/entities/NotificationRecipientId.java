package com.vishop.notification.modules.inbox.infrastructure.persistence.entities;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NotificationRecipientId implements Serializable {
  private UUID tenantId;
  private UUID notificationId;
  private UUID userId;
}
