package com.vishop.notification.modules.inbox.infrastructure.persistence.entities;

import com.vishop.notification.shared.contract.NotificationMessage.Severity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor
public class NotificationJpaEntity {
  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "source_event_id", nullable = false, updatable = false, length = 100)
  private String sourceEventId;

  @Column(nullable = false, updatable = false, length = 100)
  private String type;

  @Column(name = "subject_type", nullable = false, updatable = false, length = 100)
  private String subjectType;

  @Column(name = "subject_id", nullable = false, updatable = false, length = 200)
  private String subjectId;

  @Column(name = "shop_id", updatable = false)
  private UUID shopId;

  @Column(name = "required_permission", nullable = false, updatable = false, length = 150)
  private String requiredPermission;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private Severity severity;

  @Column(nullable = false, updatable = false, length = 300)
  private String title;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String body;

  @Column(name = "action_url", updatable = false, length = 2000)
  private String actionUrl;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(nullable = false)
  private Long version;
}
