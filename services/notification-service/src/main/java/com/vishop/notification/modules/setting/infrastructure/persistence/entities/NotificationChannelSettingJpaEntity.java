package com.vishop.notification.modules.setting.infrastructure.persistence.entities;

import com.vishop.notification.shared.contract.NotificationChannel;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "notification_settings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "channel"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationChannelSettingJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 20, updatable = false)
  private NotificationChannel channel;

  @Column(nullable = false)
  private boolean enabled;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "connection_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> connectionConfig = new LinkedHashMap<>();

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
  protected void initializeAudit() {
    var now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null || updatedAt.isBefore(createdAt)) updatedAt = createdAt;
  }

  @PreUpdate
  protected void updateAudit() {
    touch();
  }

  /** Make even an unchanged setting a versioned mutation. */
  public void touch() {
    var now = Instant.now();
    updatedAt = updatedAt != null && !now.isAfter(updatedAt) ? updatedAt.plusNanos(1000) : now;
  }

  public void restore() {
    deletedAt = null;
    touch();
  }
}
