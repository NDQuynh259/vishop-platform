package com.vishop.notification.modules.notification.infrastructure.persistence.entities;

import com.vishop.notification.modules.notification.domain.entity.NotificationDelivery.Status;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationChannelSettingJpaEntity;
import com.vishop.notification.shared.contract.NotificationChannel;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class NotificationDeliveryJpaEntity {
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
  @Setter(AccessLevel.NONE)
  private NotificationJpaEntity notification;

  @Column(name = "user_id", updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private NotificationChannel channel;

  @Column(nullable = false, updatable = false, length = 2048)
  private String destination;

  @Column(name = "setting_id", updatable = false)
  private UUID settingId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumns(
      value = {
        @JoinColumn(
            name = "tenant_id",
            referencedColumnName = "tenant_id",
            insertable = false,
            updatable = false),
        @JoinColumn(
            name = "setting_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false),
        @JoinColumn(
            name = "channel",
            referencedColumnName = "channel",
            insertable = false,
            updatable = false)
      },
      foreignKey = @ForeignKey(name = "fk_notification_delivery_setting"))
  @Setter(AccessLevel.NONE)
  private NotificationChannelSettingJpaEntity setting;

  @Column(
      name = "destination_key",
      insertable = false,
      updatable = false,
      columnDefinition = "text")
  private String destinationKey;

  @Column(name = "template_code", nullable = false, updatable = false, length = 100)
  private String templateCode = "GENERIC_NOTIFICATION";

  @Column(name = "template_version", nullable = false, updatable = false)
  private int templateVersion = 1;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "template_data", nullable = false, updatable = false, columnDefinition = "jsonb")
  private Map<String, Object> templateData = Map.of();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "lock_token")
  private UUID lockToken;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "provider_message_id", length = 300)
  private String providerMessageId;

  // Historical column retained by the existing schema. New technical errors belong in logging.
  @Column(name = "last_error", insertable = false, updatable = false, columnDefinition = "text")
  private String lastError;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @PrePersist
  void initializeAudit() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void touch() {
    Instant now = Instant.now();
    updatedAt = updatedAt != null && !now.isAfter(updatedAt) ? updatedAt.plusNanos(1000) : now;
  }
}
