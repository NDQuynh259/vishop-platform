package com.vishop.notification.modules.catalog.application.api;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Optional;
import java.util.Set;

public interface CatalogApi {
  record Type(
      String code,
      String featureCode,
      String category,
      boolean available,
      boolean defaultEnabled,
      Set<NotificationChannel> defaultChannels) {
    public Type {
      defaultChannels = Set.copyOf(defaultChannels);
    }
  }

  Optional<Type> findType(String code);
}
