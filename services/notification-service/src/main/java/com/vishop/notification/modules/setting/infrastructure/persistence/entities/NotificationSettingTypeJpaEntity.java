package com.vishop.notification.modules.setting.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "notification_setting_types",
    uniqueConstraints = @UniqueConstraint(columnNames = {"setting_id", "rule_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettingTypeJpaEntity {
  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "setting_id", nullable = false, updatable = false)
  private NotificationChannelSettingJpaEntity setting;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rule_id", nullable = false, updatable = false)
  private NotificationAlertRuleJpaEntity rule;

  public NotificationSettingTypeJpaEntity(
      UUID id, NotificationChannelSettingJpaEntity setting, NotificationAlertRuleJpaEntity rule) {
    this.id = id;
    this.setting = setting;
    this.rule = rule;
  }
}
