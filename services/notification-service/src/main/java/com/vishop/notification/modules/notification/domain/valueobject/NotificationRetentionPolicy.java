package com.vishop.notification.modules.notification.domain.valueobject;

import java.time.Duration;

/** Unified inbox recipient lifetime measured from creation time for visible and archived rows. */
public record NotificationRetentionPolicy(Duration period, int batchSize, int maxBatchesPerRun) {
  public static final int MAX_BATCH_SIZE = 10_000;
  public static final int MAX_BATCHES_PER_RUN = 100;

  public NotificationRetentionPolicy {
    if (period == null || period.isZero() || period.isNegative()) {
      throw new IllegalArgumentException("Notification retention period must be positive");
    }
    if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "Notification retention batch size must be between 1 and 10000");
    }
    if (maxBatchesPerRun < 1 || maxBatchesPerRun > MAX_BATCHES_PER_RUN) {
      throw new IllegalArgumentException(
          "Notification retention requires between 1 and 100 batches per run");
    }
  }
}
