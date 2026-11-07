package com.vishop.notification.modules.setting.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "notification_alert_rules",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "type"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationAlertRuleJpaEntity {
  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false, length = 100)
  private String type;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "in_app_enabled", nullable = false)
  private boolean inAppEnabled;

  @Column(name = "email_enabled", nullable = false)
  private boolean emailEnabled;

  @Column(name = "telegram_enabled", nullable = false)
  private boolean telegramEnabled;

  @Column(name = "lark_enabled", nullable = false)
  private boolean larkEnabled;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @PrePersist
  void initializeAudit() {
    var now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  public void touch() {
    var now = Instant.now();
    updatedAt = updatedAt != null && !now.isAfter(updatedAt) ? updatedAt.plusNanos(1000) : now;
  }
}
