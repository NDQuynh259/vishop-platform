package com.vishop.notification.modules.notification.domain.valueobject;

import java.time.Duration;

/** Visible inbox lifetime and a separate grace period after a recipient is archived. */
public record NotificationRetentionPolicy(
    Duration visibleRetention, Duration archivedRetention, int batchSize, int maxBatchesPerRun) {
  public static final int MAX_BATCH_SIZE = 10_000;
  public static final int MAX_BATCHES_PER_RUN = 100;

  public NotificationRetentionPolicy {
    if (visibleRetention == null
        || visibleRetention.isZero()
        || visibleRetention.isNegative()
        || archivedRetention == null
        || archivedRetention.isZero()
        || archivedRetention.isNegative()) {
      throw new IllegalArgumentException("Notification retention periods must be positive");
    }
    if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "Notification retention batch size must be between 1 and 10000");
    }
    if (maxBatchesPerRun < 2 || maxBatchesPerRun > MAX_BATCHES_PER_RUN) {
      throw new IllegalArgumentException(
          "Notification retention requires between 2 and 100 batches per run");
    }
  }
}
