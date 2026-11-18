package com.vishop.notification.modules.notification.infrastructure.config;

import com.vishop.notification.modules.notification.domain.valueobject.NotificationRetentionPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("notification.retention")
public record NotificationRetentionProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("90d") Duration visibleRetention,
    @DefaultValue("90d") Duration archivedRetention,
    @DefaultValue("500") int batchSize,
    @DefaultValue("10") int maxBatchesPerRun,
    @DefaultValue("300000") long fixedDelayMs,
    @DefaultValue("60000") long initialDelayMs) {
  public NotificationRetentionProperties {
    new NotificationRetentionPolicy(
        visibleRetention, archivedRetention, batchSize, maxBatchesPerRun);
    if (fixedDelayMs < 1 || initialDelayMs < 0) {
      throw new IllegalArgumentException(
          "Notification retention delay must be positive and initial delay nonnegative");
    }
  }

  public NotificationRetentionPolicy toPolicy() {
    return new NotificationRetentionPolicy(
        visibleRetention, archivedRetention, batchSize, maxBatchesPerRun);
  }
}
