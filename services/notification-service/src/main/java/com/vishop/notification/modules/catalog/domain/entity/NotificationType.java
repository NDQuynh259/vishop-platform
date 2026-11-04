package com.vishop.notification.modules.catalog.domain.entity;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Objects;
import java.util.Set;

public record NotificationType(
    String code,
    String featureCode,
    Category category,
    boolean active,
    boolean featureActive,
    boolean defaultEnabled,
    Set<NotificationChannel> defaultChannels) {
  public enum Category {
    SYSTEM,
    BUSINESS
  }

  public NotificationType {
    if (code == null || code.isBlank() || featureCode == null || featureCode.isBlank())
      throw new IllegalArgumentException("Type and feature code are required");
    Objects.requireNonNull(category);
    defaultChannels = Set.copyOf(defaultChannels);
  }
}
