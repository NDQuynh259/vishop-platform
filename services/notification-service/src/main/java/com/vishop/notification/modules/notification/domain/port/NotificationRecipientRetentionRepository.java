package com.vishop.notification.modules.notification.domain.port;

import java.time.Instant;

/**
 * Internal maintenance across tenants; each call deletes at most one independently committed batch.
 */
@FunctionalInterface
public interface NotificationRecipientRetentionRepository {
  int purgeCreatedBefore(Instant cutoffInclusive, int batchSize);
}
