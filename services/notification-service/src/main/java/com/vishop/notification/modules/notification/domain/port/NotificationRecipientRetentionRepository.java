package com.vishop.notification.modules.notification.domain.port;

import java.time.Instant;

/**
 * Internal maintenance across tenants; each call deletes at most one independently committed batch.
 */
public interface NotificationRecipientRetentionRepository {
  int purgeVisibleBefore(Instant cutoffInclusive, int batchSize);

  int purgeArchivedBefore(Instant cutoffInclusive, int batchSize);
}
