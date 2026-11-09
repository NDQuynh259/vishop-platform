package com.vishop.notification.modules.notification.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_recipients")
@Getter
@NoArgsConstructor
public class NotificationRecipientJpaEntity {
  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "notification_id", nullable = false, updatable = false)
  private UUID notificationId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "tenant_id",
        referencedColumnName = "tenant_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "notification_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false)
  })
  private NotificationJpaEntity notification;

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
