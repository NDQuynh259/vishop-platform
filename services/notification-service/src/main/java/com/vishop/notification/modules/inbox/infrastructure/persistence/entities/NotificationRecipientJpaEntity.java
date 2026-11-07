package com.vishop.notification.modules.inbox.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_recipients")
@IdClass(NotificationRecipientId.class)
@Getter
@NoArgsConstructor
public class NotificationRecipientJpaEntity {
  @Id
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Id
  @Column(name = "notification_id", nullable = false, updatable = false)
  private UUID notificationId;

  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(nullable = false)
  private Long version;
}
