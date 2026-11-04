package com.vishop.notification.modules.catalog.domain.port;

import com.vishop.notification.modules.catalog.domain.entity.NotificationType;
import java.util.Optional;

/** Returns non-deleted catalog data; active flags are retained for policy evaluation. */
public interface NotificationCatalogRepository {
  Optional<NotificationType> findType(String code);
}
