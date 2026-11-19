package com.vishop.notification.modules.notification.application.service;

import com.vishop.notification.modules.notification.application.dto.NotificationRetentionResult;
import com.vishop.notification.modules.notification.domain.port.NotificationRecipientRetentionRepository;
import com.vishop.notification.modules.notification.domain.valueobject.NotificationRetentionPolicy;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class NotificationRetentionService {
  private final NotificationRecipientRetentionRepository repository;
  private final Clock clock;

  public NotificationRetentionResult purge(NotificationRetentionPolicy policy) {
    // One fixed cutoff for the entire run; the repository commits each batch independently.
    var cutoff = clock.instant().minus(policy.period()).truncatedTo(ChronoUnit.MICROS);
    int deletedTotal = 0;
    int batches = 0;
    while (batches < policy.maxBatchesPerRun()) {
      int deleted = repository.purgeCreatedBefore(cutoff, policy.batchSize());
      deletedTotal += deleted;
      batches++;
      if (deleted < policy.batchSize()) {
        break;
      }
    }
    return new NotificationRetentionResult(deletedTotal, batches);
  }
}
