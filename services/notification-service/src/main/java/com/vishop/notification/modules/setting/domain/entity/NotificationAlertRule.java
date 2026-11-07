package com.vishop.notification.modules.setting.domain.entity;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Set;
import java.util.UUID;
import lombok.Value;

@Value
public class NotificationAlertRule {
  UUID id;
  String type;
  boolean enabled;
  Set<NotificationChannel> channels;
  long version;

  public NotificationAlertRule(
      UUID id, String type, boolean enabled, Set<NotificationChannel> channels, long version) {
    this.id = id;
    this.type = type;
    this.enabled = enabled;
    this.channels = Set.copyOf(channels);
    this.version = version;
  }
}
